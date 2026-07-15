package com.interview.back.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.interview.back.common.Result;
import com.interview.back.entity.ResumeDraft;
import com.interview.back.service.ResumeDraftService;
import com.interview.back.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/resume-drafts")
public class ResumeDraftController {
    private static final int MAX_CONTENT_LENGTH = 200_000;

    private final ResumeDraftService resumeDraftService;

    public ResumeDraftController(ResumeDraftService resumeDraftService) {
        this.resumeDraftService = resumeDraftService;
    }

    @GetMapping
    public Result<List<ResumeDraft>> listDrafts() {
        Long userId = currentUserId();
        List<ResumeDraft> drafts = resumeDraftService.list(
                Wrappers.<ResumeDraft>lambdaQuery()
                        .eq(ResumeDraft::getUserId, userId)
                        .orderByDesc(ResumeDraft::getUpdateTime));
        return Result.success(drafts);
    }

    @GetMapping("/{id}")
    public Result<ResumeDraft> getDraft(@PathVariable Long id) {
        ResumeDraft draft = ownedDraft(id);
        return draft == null
                ? Result.error(404, "草稿不存在")
                : Result.success(draft);
    }

    @PostMapping
    public Result<ResumeDraft> saveDraft(@RequestBody ResumeDraft request) {
        if (StrUtil.isBlank(request.getTitle()) || StrUtil.isBlank(request.getContentJson())) {
            return Result.error(400, "草稿标题和内容不能为空");
        }
        if (request.getContentJson().length() > MAX_CONTENT_LENGTH) {
            return Result.error(400, "草稿内容过大");
        }

        Long userId = currentUserId();
        Date now = new Date();
        if (request.getId() == null) {
            request.setUserId(userId);
            request.setStatus(0);
            request.setCreateTime(now);
            request.setUpdateTime(now);
            resumeDraftService.save(request);
            return Result.success(request);
        }

        ResumeDraft existing = ownedDraft(request.getId());
        if (existing == null) {
            return Result.error(404, "草稿不存在或无权修改");
        }
        existing.setTemplateId(request.getTemplateId());
        existing.setTitle(request.getTitle());
        existing.setContentJson(request.getContentJson());
        existing.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        existing.setUpdateTime(now);
        resumeDraftService.updateById(existing);
        return Result.success(existing);
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteDraft(@PathVariable Long id) {
        ResumeDraft draft = ownedDraft(id);
        if (draft == null) {
            return Result.error(404, "草稿不存在或无权删除");
        }
        resumeDraftService.removeById(id);
        return Result.success("草稿已删除");
    }

    private ResumeDraft ownedDraft(Long id) {
        return resumeDraftService.getOne(
                Wrappers.<ResumeDraft>lambdaQuery()
                        .eq(ResumeDraft::getId, id)
                        .eq(ResumeDraft::getUserId, currentUserId()));
    }

    private Long currentUserId() {
        return UserHolder.getUser().getId();
    }
}
