package com.interview.back.mq;

import cn.hutool.json.JSONUtil;
import com.interview.back.client.AiClient;
import com.interview.back.config.RabbitMQConfig;
import com.interview.back.entity.Resume;
import com.interview.back.mapper.ResumeMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class ResumeMqConsumer {

    private static final int MAX_PROCESSING_ATTEMPTS = 3;

    private final ResumeMapper resumeMapper;
    private final AiClient aiClient;
    private final ResumeMqProducer resumeMqProducer;
    private final Executor resumeParseExecutor;

    public ResumeMqConsumer(
            ResumeMapper resumeMapper,
            AiClient aiClient,
            ResumeMqProducer resumeMqProducer,
            @Qualifier("resumeParseExecutor") Executor resumeParseExecutor
    ) {
        this.resumeMapper = resumeMapper;
        this.aiClient = aiClient;
        this.resumeMqProducer = resumeMqProducer;
        this.resumeParseExecutor = resumeParseExecutor;
    }

    @RabbitListener(queues = RabbitMQConfig.RESUME_QUEUE)
    public CompletableFuture<Void> handleResumeParseTask(
            ResumeParseMessage task,
            @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered
    ) {
        // JDBC is blocking; keep even the initial claim off the Rabbit listener thread.
        // Spring AMQP acknowledges only after the returned future completes.
        try {
            return CompletableFuture.supplyAsync(
                    () -> claimResume(task, redelivered), resumeParseExecutor
            ).thenCompose(resume -> {
                if (resume == null) {
                    return CompletableFuture.<Void>completedFuture(null);
                }
                CompletableFuture<Void> processing = CompletableFuture
                        .supplyAsync(() -> preparePdf(resume, task), resumeParseExecutor)
                        .thenCompose(parsed -> aiClient.diagnoseResumeAsync(
                                parsed.text(), parsed.imageBase64(), "image/png"
                        ).thenApply(result -> new DiagnosisPayload(parsed.text(), result)))
                        .thenAcceptAsync(payload -> saveDiagnosis(resume, task, payload), resumeParseExecutor);

                return processing.<Void>handle((ignored, error) -> {
                    if (error == null) {
                        return null;
                    }
                    Throwable cause = unwrap(error);
                    if (cause instanceof AmqpRejectAndDontRequeueException reject) {
                        throw reject;
                    }
                    if (isExecutorRejection(cause)) {
                        throw requeueAfterExecutorRejection(cause, task);
                    }
                    handleTransientFailure(resume, task,
                            cause instanceof Exception exception
                                    ? exception : new IllegalStateException(cause));
                    return null;
                });
            }).exceptionallyCompose(error ->
                    CompletableFuture.failedFuture(requeueIfExecutorRejected(error, task)));
        } catch (RuntimeException error) {
            // Spring AMQP requires an async result even if scheduling fails immediately.
            return CompletableFuture.failedFuture(requeueIfExecutorRejected(error, task));
        }
    }

    private Resume claimResume(ResumeParseMessage task, Boolean redelivered) {
        validateTask(task);
        Long resumeId = task.resumeId();
        log.info(
                "Received resume parse task, messageId={}, resumeId={}, attempt={}, redelivered={}",
                task.messageId(), resumeId, task.attempt(), Boolean.TRUE.equals(redelivered)
        );
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new AmqpRejectAndDontRequeueException("Resume not found for ID: " + resumeId);
        }
        if (resume.getStatus() != null && (resume.getStatus() == 1 || resume.getStatus() == 2)) {
            log.info("Skipping terminal resume task, messageId={}, resumeId={}, status={}",
                    task.messageId(), resumeId, resume.getStatus());
            return null;
        }
        if (Integer.valueOf(3).equals(resume.getStatus()) && Boolean.TRUE.equals(redelivered)) {
            resumeMapper.releaseForRetry(resumeId);
        }
        if (resumeMapper.claimPending(resumeId) == 0) {
            log.info("Resume task already claimed by another consumer, messageId={}, resumeId={}",
                    task.messageId(), resumeId);
            return null;
        }
        return resume;
    }

    private ParsedResume preparePdf(Resume resume, ResumeParseMessage task) {
        File pdfFile = new File(resume.getFileUrl());
        if (!pdfFile.exists()) {
            failPermanently(resume, task, "File not found: " + resume.getFileUrl(), null);
        }
        String parsedText = parsePdfText(pdfFile, resume, task);
        if (parsedText != null && parsedText.length() > 4000) {
            parsedText = parsedText.substring(0, 4000);
        }
        return new ParsedResume(parsedText == null ? "" : parsedText, renderFirstPage(pdfFile));
    }

    private void saveDiagnosis(Resume resume, ResumeParseMessage task, DiagnosisPayload payload) {
        String aiResult = stripMarkdownFence(payload.result());
        if (!JSONUtil.isTypeJSON(aiResult)) {
            throw new IllegalStateException("Python agent returned invalid diagnosis JSON");
        }
        String extractedText;
        try {
            Map<String, Object> diagnosis = JSONUtil.toBean(aiResult, Map.class);
            Object extracted = diagnosis.get("extracted_text");
            extractedText = extracted == null ? "" : extracted.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse diagnosis JSON", e);
        }

        if (cn.hutool.core.util.StrUtil.isNotBlank(extractedText)
                && cn.hutool.core.util.StrUtil.isBlank(payload.parsedText())) {
            resume.setParsedContent(extractedText);
        } else {
            resume.setParsedContent(payload.parsedText());
        }
        resume.setDiagnosisResult(aiResult);
        resume.setStatus(1);
        if (resumeMapper.updateById(resume) != 1) {
            throw new IllegalStateException("Failed to save resume diagnosis for ID: " + task.resumeId());
        }
        log.info("Resume parsed successfully, messageId={}, resumeId={}, attempt={}",
                task.messageId(), task.resumeId(), task.attempt());
    }

    private Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private boolean isExecutorRejection(Throwable error) {
        return error instanceof TaskRejectedException || error instanceof RejectedExecutionException;
    }

    private RuntimeException requeueIfExecutorRejected(Throwable error, ResumeParseMessage task) {
        Throwable cause = unwrap(error);
        if (isExecutorRejection(cause)) {
            return requeueAfterExecutorRejection(cause, task);
        }
        return cause instanceof RuntimeException runtime ? runtime : new CompletionException(cause);
    }

    private ImmediateRequeueAmqpException requeueAfterExecutorRejection(
            Throwable error, ResumeParseMessage task
    ) {
        log.warn("Resume parse executor rejected task; asking broker to requeue, messageId={}",
                task == null ? null : task.messageId(), error);
        return new ImmediateRequeueAmqpException("Resume parse executor unavailable", error);
    }

    private record ParsedResume(String text, String imageBase64) {}

    private record DiagnosisPayload(String parsedText, String result) {}

    private void validateTask(ResumeParseMessage task) {
        if (task == null || task.resumeId() == null || task.messageId() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid resume parse message");
        }
    }

    private String parsePdfText(
            File pdfFile,
            Resume resume,
            ResumeParseMessage task
    ) {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            failPermanently(resume, task, "Invalid or unreadable PDF", e);
            return "";
        }
    }

    private String renderFirstPage(File pdfFile) {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            if (document.getNumberOfPages() == 0) {
                return "";
            }
            org.apache.pdfbox.rendering.PDFRenderer renderer =
                    new org.apache.pdfbox.rendering.PDFRenderer(document);
            java.awt.image.BufferedImage image = renderer.renderImageWithDPI(
                    0,
                    150,
                    org.apache.pdfbox.rendering.ImageType.RGB
            );
            try (java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                javax.imageio.ImageIO.write(image, "png", output);
                return cn.hutool.core.codec.Base64.encode(output.toByteArray());
            }
        } catch (Exception e) {
            log.warn("Failed to render PDF first page; continuing with extracted text", e);
            return "";
        }
    }

    private String stripMarkdownFence(String content) {
        if (content == null) {
            return "";
        }
        String result = content.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.trim();
    }

    private void handleTransientFailure(
            Resume resume,
            ResumeParseMessage task,
            Exception error
    ) {
        log.warn(
                "Resume parse attempt failed, messageId={}, resumeId={}, attempt={}/{}",
                task.messageId(),
                task.resumeId(),
                task.attempt() + 1,
                MAX_PROCESSING_ATTEMPTS,
                error
        );

        if (task.attempt() + 1 < MAX_PROCESSING_ATTEMPTS) {
            if (resumeMapper.releaseForRetry(task.resumeId()) == 0) {
                failPermanently(
                        resume,
                        task,
                        "Could not release resume status for retry",
                        error
                );
            }
            try {
                resumeMqProducer.sendRetryTask(task.nextAttempt());
                log.info(
                        "Scheduled delayed resume retry, messageId={}, resumeId={}, nextAttempt={}",
                        task.messageId(),
                        task.resumeId(),
                        task.attempt() + 1
                );
                return;
            } catch (Exception publishError) {
                failPermanently(
                        resume,
                        task,
                        "Failed to schedule resume retry",
                        publishError
                );
            }
        }
        failPermanently(resume, task, "Resume parsing retries exhausted", error);
    }

    private void failPermanently(
            Resume resume,
            ResumeParseMessage task,
            String message,
            Exception cause
    ) {
        resume.setStatus(2);
        resumeMapper.updateById(resume);
        log.error(
                "Resume task moved to DLQ, messageId={}, resumeId={}, reason={}",
                task.messageId(),
                task.resumeId(),
                message,
                cause
        );
        if (cause == null) {
            throw new AmqpRejectAndDontRequeueException(message);
        }
        throw new AmqpRejectAndDontRequeueException(message, cause);
    }
}
