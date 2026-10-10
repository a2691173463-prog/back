package com.interview.back.mq;

import com.interview.back.client.AiClient;
import com.interview.back.entity.Resume;
import com.interview.back.mapper.ResumeMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResumeMqConsumerTest {

    @TempDir
    Path tempDir;

    private ResumeMapper resumeMapper;
    private AiClient aiClient;
    private ResumeMqProducer producer;
    private ResumeMqConsumer consumer;
    private Resume resume;

    @BeforeEach
    void setUp() throws Exception {
        resumeMapper = mock(ResumeMapper.class);
        aiClient = mock(AiClient.class);
        producer = mock(ResumeMqProducer.class);
        consumer = new ResumeMqConsumer(resumeMapper, aiClient, producer, Runnable::run);

        Path pdf = tempDir.resolve("resume.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }

        resume = new Resume();
        resume.setId(7L);
        resume.setStatus(0);
        resume.setFileUrl(pdf.toString());
        when(resumeMapper.selectById(7L)).thenReturn(resume);
        when(resumeMapper.claimPending(7L)).thenReturn(1);
        when(resumeMapper.updateById(any(Resume.class))).thenReturn(1);
    }

    @Test
    void marksResumeSuccessfulAfterValidDiagnosis() {
        when(aiClient.diagnoseResumeAsync(any(), any(), eq("image/png")))
                .thenReturn(CompletableFuture.completedFuture("""
                        {"extracted_text":"","advantages":[],"disadvantages":[],"suggestions":[]}
                        """));

        consumer.handleResumeParseTask(message(0), false).join();

        assertThat(resume.getStatus()).isEqualTo(1);
        verify(resumeMapper).updateById(resume);
        verifyNoInteractions(producer);
    }

    @Test
    void skipsAlreadyCompletedDuplicate() {
        resume.setStatus(1);

        consumer.handleResumeParseTask(message(0), false).join();

        verify(resumeMapper, never()).claimPending(any());
        verifyNoInteractions(aiClient, producer);
    }

    @Test
    void schedulesDelayedRetryForTransientFailure() {
        when(aiClient.diagnoseResumeAsync(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("temporary timeout")));
        when(resumeMapper.releaseForRetry(7L)).thenReturn(1);

        consumer.handleResumeParseTask(message(0), false).join();

        verify(producer).sendRetryTask(argThat(task ->
                task.resumeId().equals(7L)
                        && task.attempt() == 1
                        && task.messageId().equals("message-1")
        ));
        verify(resumeMapper).releaseForRetry(7L);
    }

    @Test
    void marksFailedAndRejectsAfterRetriesExhausted() {
        when(aiClient.diagnoseResumeAsync(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("still unavailable")));

        assertThatThrownBy(() -> consumer.handleResumeParseTask(message(2), false).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(resume.getStatus()).isEqualTo(2);
        verify(resumeMapper).updateById(resume);
        verifyNoInteractions(producer);
    }

    @Test
    void waitsForModelAndDatabaseBeforeCompletingListenerFuture() {
        CompletableFuture<String> modelResponse = new CompletableFuture<>();
        when(aiClient.diagnoseResumeAsync(any(), any(), any())).thenReturn(modelResponse);

        CompletableFuture<Void> delivery = consumer.handleResumeParseTask(message(0), false);

        assertThat(delivery).isNotDone();
        verify(resumeMapper, never()).updateById(any(Resume.class));
        modelResponse.complete("""
                {"extracted_text":"","advantages":[],"disadvantages":[],"suggestions":[]}
                """);
        delivery.join();

        assertThat(resume.getStatus()).isEqualTo(1);
        verify(resumeMapper).updateById(resume);
        verify(aiClient, never()).diagnoseResume(any(), any(), any());
    }

    @Test
    void doesNotBlockListenerThreadOnInitialDatabaseClaim() {
        ArrayDeque<Runnable> queuedWork = new ArrayDeque<>();
        consumer = new ResumeMqConsumer(resumeMapper, aiClient, producer, queuedWork::add);
        when(aiClient.diagnoseResumeAsync(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("""
                        {"extracted_text":"","advantages":[],"disadvantages":[],"suggestions":[]}
                        """));

        CompletableFuture<Void> delivery = consumer.handleResumeParseTask(message(0), false);

        assertThat(delivery).isNotDone();
        verifyNoInteractions(resumeMapper);
        queuedWork.remove().run();
        verify(resumeMapper).claimPending(7L);
        assertThat(delivery).isNotDone();
        while (!queuedWork.isEmpty()) {
            queuedWork.remove().run();
        }
        delivery.join();
        assertThat(resume.getStatus()).isEqualTo(1);
    }

    @Test
    void requeuesIfInitialClaimCannotBeScheduled() {
        consumer = new ResumeMqConsumer(resumeMapper, aiClient, producer,
                command -> { throw new RejectedExecutionException("pool stopped"); });

        assertThatThrownBy(() -> consumer.handleResumeParseTask(message(0), false).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ImmediateRequeueAmqpException.class);
        verifyNoInteractions(resumeMapper, aiClient, producer);
    }

    @Test
    void requeuesIfPdfStageCannotBeScheduledAfterClaim() {
        AtomicInteger submissions = new AtomicInteger();
        Executor rejectingSecondStage = command -> {
            if (submissions.incrementAndGet() == 2) {
                throw new RejectedExecutionException("pool full");
            }
            command.run();
        };
        consumer = new ResumeMqConsumer(resumeMapper, aiClient, producer, rejectingSecondStage);

        assertThatThrownBy(() -> consumer.handleResumeParseTask(message(0), false).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ImmediateRequeueAmqpException.class);
        verify(resumeMapper).claimPending(7L);
        verify(resumeMapper, never()).updateById(any(Resume.class));
        verifyNoInteractions(aiClient, producer);
    }

    @Test
    void redeliveryReleasesPreviouslyClaimedResume() {
        resume.setStatus(3);
        when(resumeMapper.releaseForRetry(7L)).thenReturn(1);
        when(aiClient.diagnoseResumeAsync(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("""
                        {"extracted_text":"","advantages":[],"disadvantages":[],"suggestions":[]}
                        """));

        consumer.handleResumeParseTask(message(0), true).join();

        verify(resumeMapper).releaseForRetry(7L);
        verify(resumeMapper).claimPending(7L);
        assertThat(resume.getStatus()).isEqualTo(1);
    }

    private ResumeParseMessage message(int attempt) {
        return new ResumeParseMessage("message-1", 7L, attempt, 1L);
    }
}
