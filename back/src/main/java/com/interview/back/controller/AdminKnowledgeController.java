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
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    private static final long MAX_PDF_SIZE = 100L * 1024 * 1024;

    private final AiClient aiClient;

    public AdminKnowledgeController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @PostMapping("/documents")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", defaultValue = "通用") String category,
            @RequestParam(value = "scope", defaultValue = "GLOBAL") String scope,
            @RequestParam(value = "positionCode", defaultValue = "GENERAL") String positionCode) {
        String fileName = file.getOriginalFilename();
        if (file.isEmpty() || fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return Result.error(400, "请上传有效的 PDF 文件。");
        }
        if (file.getSize() > MAX_PDF_SIZE) {
            return Result.error(400, "PDF 文件不能超过 100MB。");
        }
        String normalizedScope = scope == null ? "GLOBAL" : scope.trim().toUpperCase(Locale.ROOT);
        if (!"GLOBAL".equals(normalizedScope) && !"POSITION".equals(normalizedScope)) {
            return Result.error(400, "管理员文档范围只能是 GLOBAL 或 POSITION。");
        }
        String normalizedPosition = "POSITION".equals(normalizedScope)
                ? normalizePosition(positionCode)
                : "GENERAL";
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
            return Result.success(aiClient.addKnowledgeDocument(
                    UserHolder.getUser().getId(), resolvedTitle, fileName,
                    resolvedCategory, content, normalizedScope, normalizedPosition));
        } catch (Exception e) {
            return Result.error(500, "共享知识文档入库失败：" + e.getMessage());
        }
    }

    @GetMapping("/documents")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(aiClient.listKnowledgeDocuments(
                UserHolder.getUser().getId(), true, true));
    }

    @DeleteMapping("/documents/{documentId}")
    public Result<String> delete(@PathVariable String documentId) {
        boolean deleted = aiClient.deleteKnowledgeDocument(
                documentId, UserHolder.getUser().getId(), true);
        return deleted ? Result.success("删除成功") : Result.error(404, "知识文档不存在。");
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(aiClient.getKnowledgeRetrievalStats());
    }

    private String normalizePosition(String positionCode) {
        if (positionCode == null || positionCode.isBlank()) {
            return "GENERAL";
        }
        String normalized = positionCode.trim().toUpperCase(Locale.ROOT);
        return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
    }
}
