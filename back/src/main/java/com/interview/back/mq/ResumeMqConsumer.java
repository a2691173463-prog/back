package com.interview.back.mq;

import cn.hutool.json.JSONUtil;
import com.interview.back.client.AiClient;
import com.interview.back.config.RabbitMQConfig;
import com.interview.back.entity.Resume;
import com.interview.back.mapper.ResumeMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

@Slf4j
@Component
public class ResumeMqConsumer {

    private final ResumeMapper resumeMapper;
    private final AiClient aiClient;

    public ResumeMqConsumer(ResumeMapper resumeMapper, AiClient aiClient) {
        this.resumeMapper = resumeMapper;
        this.aiClient = aiClient;
    }

    @RabbitListener(queues = RabbitMQConfig.RESUME_QUEUE)
    public void handleResumeParseTask(Long resumeId) {
        log.info("Received resume parse task, resumeId = {}", resumeId);
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            log.error("Resume not found for ID: {}", resumeId);
            return;
        }

        try {
            // 1. Parse PDF text (for fallback and interview use)
            File pdfFile = new File(resume.getFileUrl());
            if (!pdfFile.exists()) {
                throw new RuntimeException("File not found: " + resume.getFileUrl());
            }

            String parsedText = "";
            try (PDDocument document = PDDocument.load(pdfFile)) {
                PDFTextStripper stripper = new PDFTextStripper();
                parsedText = stripper.getText(document);
            }
            
            if (parsedText != null && parsedText.length() > 4000) {
                parsedText = parsedText.substring(0, 4000);
            }

            // 2. 将 PDF 第一页渲染为高清图片（DPI=150）转为 Base64
            String base64Image = "";
            try (PDDocument document = PDDocument.load(pdfFile)) {
                if (document.getNumberOfPages() > 0) {
                    org.apache.pdfbox.rendering.PDFRenderer pdfRenderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                    java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(0, 150, org.apache.pdfbox.rendering.ImageType.RGB);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(bim, "png", baos);
                    base64Image = cn.hutool.core.codec.Base64.encode(baos.toByteArray());
                }
            } catch (Exception ex) {
                log.error("Failed to render PDF page to image", ex);
            }

            // 3. 调用 AI 进行多模态视觉 + 内容双重诊断
            String aiResult = aiClient.diagnoseResume(parsedText, base64Image, "image/png");

            // 4. 清理大模型可能携带的 markdown 标记
            if (aiResult.startsWith("```json")) {
                aiResult = aiResult.substring(7, aiResult.length() - 3).trim();
            } else if (aiResult.startsWith("```")) {
                aiResult = aiResult.substring(3, aiResult.length() - 3).trim();
            }

            // 5. 解析并处理可能返回的 OCR 文本
            String extractedText = "";
            try {
                Map<String, Object> evalMap = JSONUtil.toBean(aiResult, Map.class);
                if (evalMap.containsKey("extracted_text")) {
                    extractedText = evalMap.get("extracted_text").toString();
                }
            } catch (Exception e) {
                // Ignore JSON parse errors
            }

            // 6. 保存诊断和文本内容（如果是扫描版 PDF，优先使用大模型 OCR 识别出的文本）
            if (cn.hutool.core.util.StrUtil.isNotBlank(extractedText) && 
                (parsedText == null || cn.hutool.core.util.StrUtil.isBlank(parsedText.trim()))) {
                resume.setParsedContent(extractedText);
            } else {
                resume.setParsedContent(parsedText);
            }

            resume.setDiagnosisResult(aiResult);
            resume.setStatus(1); // 1 = Success
            resumeMapper.updateById(resume);
            log.info("Resume parsed and VL diagnosed successfully, resumeId = {}", resumeId);

        } catch (Exception e) {
            log.error("Failed to parse resume, resumeId = {}", resumeId, e);
            resume.setStatus(2); // 2 = Failed
            resumeMapper.updateById(resume);
        }
    }
}
