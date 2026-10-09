package com.interview.back.controller;

import com.interview.back.client.AiClient;
import com.interview.back.common.Result;
import com.interview.back.utils.UserHolder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final long MAX_PDF_SIZE = 100L * 1024 * 1024;

    private final AiClient aiClient;

    public KnowledgeController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @PostMapping("/documents")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", defaultValue = "通用") String category) {
        String fileName = file.getOriginalFilename();
        if (file.isEmpty() || fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            return Result.error(400, "请上传有效的 PDF 文件。");
        }
        if (file.getSize() > MAX_PDF_SIZE) {
            return Result.error(400, "PDF 文件不能超过 100MB。");
        }
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            String content = new PDFTextStripper().getText(document).trim();
            if (content.length() < 20) {
                return Result.error(400, "PDF 未提取到足够文本，暂不支持纯扫描图片 PDF。");
            }
            String resolvedTitle = title == null || title.isBlank()
                    ? fileName.substring(0, fileName.length() - 4)
                    : title.trim();
            String resolvedCategory = category == null || category.isBlank()
                    ? "通用"
                    : category.trim();
            Long userId = UserHolder.getUser().getId();
            return Result.success(aiClient.addKnowledgeDocument(
                    userId, resolvedTitle, fileName, resolvedCategory, content,
                    "PRIVATE", "GENERAL"));
        } catch (Exception e) {
            return Result.error(500, "知识文档入库失败：" + e.getMessage());
        }
    }

    @GetMapping("/documents")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(aiClient.listKnowledgeDocuments(
                UserHolder.getUser().getId(), true, false));
    }

    @DeleteMapping("/documents/{documentId}")
    public Result<String> delete(@PathVariable String documentId) {
        boolean deleted = aiClient.deleteKnowledgeDocument(
                documentId, UserHolder.getUser().getId(), false);
        return deleted ? Result.success("删除成功") : Result.error(404, "知识文档不存在。");
    }
}
