package com.interview.back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.back.common.Result;
import com.interview.back.entity.Resume;
import com.interview.back.service.ResumeService;
import com.interview.back.utils.UserHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeService resumeService;
    private final com.interview.back.service.EnergyService energyService;

    public ResumeController(ResumeService resumeService, com.interview.back.service.EnergyService energyService) {
        this.resumeService = resumeService;
        this.energyService = energyService;
    }

    @PostMapping("/upload")
    public Result<Long> uploadResume(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            return Result.error(400, "Please upload a valid PDF file");
        }
        Long userId = UserHolder.getUser().getId();

        // 校验算力能量值是否足够进行诊断（预估消耗 150 EP）
        if (!energyService.hasEnoughEnergy(userId, 150)) {
            return Result.error(403, "您的 AI 算力能量不足以完成本次简历诊断（本操作需 150 EP），请点击左侧签到补充能量！");
        }

        Long resumeId = resumeService.uploadAndParse(file, userId);
        return Result.success(resumeId);
    }

    @GetMapping("/list")
    public Result<List<Resume>> getResumeList() {
        Long userId = UserHolder.getUser().getId();
        List<Resume> list = resumeService.list(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getCreateTime));
        for (Resume resume : list) {
            resume.setFileUrl(null); // Hide absolute path
        }
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<Resume> getResumeStatus(@PathVariable("id") Long id) {
        Resume resume = resumeService.getById(id);
        if (resume != null) {
            resume.setFileUrl(null); 
        }
        return Result.success(resume);
    }
}
