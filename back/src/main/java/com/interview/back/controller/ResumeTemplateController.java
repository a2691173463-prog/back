package com.interview.back.controller;

import cn.hutool.core.util.StrUtil;
import com.interview.back.common.Result;
import com.interview.back.client.AiClient;
import com.interview.back.dto.ResumeOptimizeRequest;
import com.interview.back.entity.ResumeTemplate;
import com.interview.back.service.ResumeTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ResumeTemplateController {

    private final ResumeTemplateService resumeTemplateService;
    private final AiClient aiClient;

    public ResumeTemplateController(ResumeTemplateService resumeTemplateService, AiClient aiClient) {
        this.resumeTemplateService = resumeTemplateService;
        this.aiClient = aiClient;
    }

    // ==================== 用户端接口 ====================

    /**
     * 获取所有简历模板列表
     */
    @GetMapping("/templates/list")
    public Result<List<ResumeTemplate>> listTemplates() {
        List<ResumeTemplate> list = resumeTemplateService.list();
        return Result.success(list);
    }

    @PostMapping("/templates/optimize")
    public Result<String> optimizeContent(@RequestBody ResumeOptimizeRequest request) {
        if (request == null || StrUtil.isBlank(request.getContent())) {
            return Result.error(400, "请先填写需要优化的内容");
        }
        if (request.getContent().length() > 6000) {
            return Result.error(400, "单次优化内容不能超过 6000 字");
        }
        String optimized = aiClient.optimizeResumeSection(
                StrUtil.blankToDefault(request.getSectionType(), "项目经历"),
                StrUtil.blankToDefault(request.getTargetRole(), "校招岗位"),
                request.getContent());
        return Result.success(optimized);
    }

    // ==================== 管理员接口 (被 AdminInterceptor 拦截) ====================

    /**
     * 添加简历模板
     */
    @PostMapping("/admin/templates")
    public Result<String> addTemplate(@RequestBody ResumeTemplate template) {
        if (StrUtil.isBlank(template.getName()) || StrUtil.isBlank(template.getCategory())) {
            return Result.error(400, "模板名称和类型不能为空");
        }
        template.setThumbnailUrl(StrUtil.blankToDefault(template.getThumbnailUrl(), ""));
        template.setDownloadUrl(StrUtil.blankToDefault(template.getDownloadUrl(), ""));
        resumeTemplateService.save(template);
        return Result.success("模板添加成功");
    }

    /**
     * 删除简历模板
     */
    @DeleteMapping("/admin/templates/{id}")
    public Result<String> deleteTemplate(@PathVariable("id") Long id) {
        resumeTemplateService.removeById(id);
        return Result.success("模板删除成功");
    }
}
