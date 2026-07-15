package com.interview.back.controller;

import cn.hutool.core.util.StrUtil;
import com.interview.back.common.Result;
import com.interview.back.entity.ResumeTemplate;
import com.interview.back.service.ResumeTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ResumeTemplateController {

    private final ResumeTemplateService resumeTemplateService;

    public ResumeTemplateController(ResumeTemplateService resumeTemplateService) {
        this.resumeTemplateService = resumeTemplateService;
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

    // ==================== 管理员接口 (被 AdminInterceptor 拦截) ====================

    /**
     * 添加简历模板
     */
    @PostMapping("/admin/templates")
    public Result<String> addTemplate(@RequestBody ResumeTemplate template) {
        if (StrUtil.isBlank(template.getName()) || StrUtil.isBlank(template.getDownloadUrl())) {
            return Result.error(400, "模板名称和下载地址不能为空");
        }
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
