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

import java.nio.file.Path;

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
        consumer = new ResumeMqConsumer(resumeMapper, aiClient, producer);

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
    }

    @Test
    void marksResumeSuccessfulAfterValidDiagnosis() {
        when(aiClient.diagnoseResume(any(), any(), eq("image/png")))
                .thenReturn("""
                        {"extracted_text":"","advantages":[],"disadvantages":[],"suggestions":[]}
                        """);

        consumer.handleResumeParseTask(message(0), false);

        assertThat(resume.getStatus()).isEqualTo(1);
        verify(resumeMapper).updateById(resume);
        verifyNoInteractions(producer);
    }

    @Test
    void skipsAlreadyCompletedDuplicate() {
        resume.setStatus(1);

        consumer.handleResumeParseTask(message(0), false);

        verify(resumeMapper, never()).claimPending(any());
        verifyNoInteractions(aiClient, producer);
    }

    @Test
    void schedulesDelayedRetryForTransientFailure() {
        when(aiClient.diagnoseResume(any(), any(), any()))
                .thenThrow(new IllegalStateException("temporary timeout"));
        when(resumeMapper.releaseForRetry(7L)).thenReturn(1);

        consumer.handleResumeParseTask(message(0), false);

        verify(producer).sendRetryTask(argThat(task ->
                task.resumeId().equals(7L)
                        && task.attempt() == 1
                        && task.messageId().equals("message-1")
        ));
        verify(resumeMapper).releaseForRetry(7L);
    }

    @Test
    void marksFailedAndRejectsAfterRetriesExhausted() {
        when(aiClient.diagnoseResume(any(), any(), any()))
                .thenThrow(new IllegalStateException("still unavailable"));

        assertThatThrownBy(() -> consumer.handleResumeParseTask(message(2), false))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("retries exhausted");

        assertThat(resume.getStatus()).isEqualTo(2);
        verify(resumeMapper).updateById(resume);
        verifyNoInteractions(producer);
    }

    private ResumeParseMessage message(int attempt) {
        return new ResumeParseMessage("message-1", 7L, attempt, 1L);
    }
}
