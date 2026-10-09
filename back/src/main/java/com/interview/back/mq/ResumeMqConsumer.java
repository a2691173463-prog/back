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
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class ResumeMqConsumer {

    private static final int MAX_PROCESSING_ATTEMPTS = 3;

    private final ResumeMapper resumeMapper;
    private final AiClient aiClient;
    private final ResumeMqProducer resumeMqProducer;

    public ResumeMqConsumer(
            ResumeMapper resumeMapper,
            AiClient aiClient,
            ResumeMqProducer resumeMqProducer
    ) {
        this.resumeMapper = resumeMapper;
        this.aiClient = aiClient;
        this.resumeMqProducer = resumeMqProducer;
    }

    @RabbitListener(queues = RabbitMQConfig.RESUME_QUEUE)
    public void handleResumeParseTask(
            ResumeParseMessage task,
            @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered
    ) {
        validateTask(task);
        Long resumeId = task.resumeId();
        log.info(
                "Received resume parse task, messageId={}, resumeId={}, attempt={}, redelivered={}",
                task.messageId(),
                resumeId,
                task.attempt(),
                Boolean.TRUE.equals(redelivered)
        );

        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new AmqpRejectAndDontRequeueException(
                    "Resume not found for ID: " + resumeId
            );
        }
        if (resume.getStatus() != null && (resume.getStatus() == 1 || resume.getStatus() == 2)) {
            log.info(
                    "Skipping terminal resume task, messageId={}, resumeId={}, status={}",
                    task.messageId(),
                    resumeId,
                    resume.getStatus()
            );
            return;
        }

        if (Integer.valueOf(3).equals(resume.getStatus()) && Boolean.TRUE.equals(redelivered)) {
            resumeMapper.releaseForRetry(resumeId);
        }
        if (resumeMapper.claimPending(resumeId) == 0) {
            log.info(
                    "Resume task already claimed by another consumer, messageId={}, resumeId={}",
                    task.messageId(),
                    resumeId
            );
            return;
        }

        try {
            File pdfFile = new File(resume.getFileUrl());
            if (!pdfFile.exists()) {
                failPermanently(resume, task, "File not found: " + resume.getFileUrl(), null);
            }

            String parsedText = parsePdfText(pdfFile, resume, task);
            if (parsedText != null && parsedText.length() > 4000) {
                parsedText = parsedText.substring(0, 4000);
            }

            String base64Image = renderFirstPage(pdfFile);
            String aiResult = aiClient.diagnoseResume(parsedText, base64Image, "image/png");
            aiResult = stripMarkdownFence(aiResult);
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
                    && cn.hutool.core.util.StrUtil.isBlank(parsedText)) {
                resume.setParsedContent(extractedText);
            } else {
                resume.setParsedContent(parsedText);
            }

            resume.setDiagnosisResult(aiResult);
            resume.setStatus(1);
            resumeMapper.updateById(resume);
            log.info(
                    "Resume parsed successfully, messageId={}, resumeId={}, attempt={}",
                    task.messageId(),
                    resumeId,
                    task.attempt()
            );
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;
        } catch (Exception e) {
            handleTransientFailure(resume, task, e);
        }
    }

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
