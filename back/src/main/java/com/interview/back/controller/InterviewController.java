package com.interview.back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.back.common.Result;
import com.interview.back.config.RedisRateLimiter;
import com.interview.back.entity.InterviewRecord;
import com.interview.back.service.InterviewService;
import com.interview.back.utils.UserHolder;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/interview")
public class InterviewController {

    private static final DefaultRedisScript<Long> SAFE_UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final InterviewService interviewService;
    private final RedisRateLimiter redisRateLimiter;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final com.interview.back.service.EnergyService energyService;

    public InterviewController(InterviewService interviewService, RedisRateLimiter redisRateLimiter,
                               org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate,
                               com.interview.back.service.EnergyService energyService) {
        this.interviewService = interviewService;
        this.redisRateLimiter = redisRateLimiter;
        this.stringRedisTemplate = stringRedisTemplate;
        this.energyService = energyService;
    }

    @PostMapping("/init")
    public Result<Long> initInterview(
            @RequestParam("resumeId") Long resumeId,
            @RequestParam(value = "mode", defaultValue = "standard") String mode,
            @RequestParam(value = "position", defaultValue = "GENERAL") String position) {
        Long userId = UserHolder.getUser().getId();
        
        // 1. 防双击分布式锁（SETNX），防快速多次点击
        String lockKey = com.interview.back.utils.RedisConstants.LOCK_INTERVIEW_INIT_KEY + userId;
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 5, java.util.concurrent.TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return Result.error(429, "面试初始化正在处理中，请勿重复点击");
        }

        try {
            // 2. 校验算力能量值是否足够（预估消耗 50 EP）
            if (!energyService.hasEnoughEnergy(userId, 50)) {
                return Result.error(403, "您的 AI 算力能量不足以开启模拟面试（本操作需 50 EP），请点击左侧签到补充能量！");
            }

            Long interviewId = interviewService.initInterview(resumeId, userId, mode, position);
            return Result.success(interviewId);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } finally {
            // 只释放自己持有的锁，避免锁过期后误删其他请求重新获取的锁
            safeUnlock(lockKey, lockValue);
        }
    }

    @GetMapping("/chat")
    public SseEmitter chat(@RequestParam("interviewId") Long interviewId, @RequestParam("message") String message) {
        Long userId = UserHolder.getUser().getId();
        
        // 1. 校验算力能量值是否足够（对话一轮预估消耗 30 EP）
        if (!energyService.hasEnoughEnergy(userId, 30)) {
            SseEmitter errorEmitter = new SseEmitter();
            errorEmitter.completeWithError(new RuntimeException("AI 算力能量不足，对话终止（对话需 30 EP），请先签到补充能量！"));
            return errorEmitter;
        }

        // 2. 滑动窗口限流
        String limitKey = "rate_limit:chat:user:" + userId;
        if (!redisRateLimiter.isAllowed(limitKey, 10, 60)) {
            SseEmitter errorEmitter = new SseEmitter();
            errorEmitter.completeWithError(new RuntimeException("Rate limit exceeded. Please wait a moment."));
            return errorEmitter;
        }
        return interviewService.chat(interviewId, message);
    }

    @PostMapping("/end")
    public Result<InterviewRecord> endInterview(@RequestParam("interviewId") Long interviewId) {
        Long userId = UserHolder.getUser().getId();
        
        // 1. 校验算力能量值是否足够进行评估（评估需 200 EP）
        if (!energyService.hasEnoughEnergy(userId, 200)) {
            return Result.error(403, "您的 AI 算力能量不足以生成面试评估报告（评估需 200 EP），请点击左侧签到补充能量！");
        }

        // 2. 评估防刷分布式锁（SETNX）
        String lockKey = com.interview.back.utils.RedisConstants.LOCK_INTERVIEW_END_KEY + interviewId;
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 30, java.util.concurrent.TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return Result.error(429, "评估报告正在生成中，请勿重复点击，请耐心等待...");
        }

        try {
            InterviewRecord record = interviewService.endInterview(interviewId);
            return Result.success(record);
        } finally {
            // 仅在 value 仍属于本次请求时原子删除，避免误删后来者的锁
            safeUnlock(lockKey, lockValue);
        }
    }

    private void safeUnlock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(
                SAFE_UNLOCK_SCRIPT,
                Collections.singletonList(lockKey),
                lockValue
        );
    }

    @GetMapping("/list")
    public Result<List<InterviewRecord>> getInterviewList() {
        Long userId = UserHolder.getUser().getId();
        List<InterviewRecord> list = interviewService.list(new LambdaQueryWrapper<InterviewRecord>()
                .eq(InterviewRecord::getUserId, userId)
                .orderByDesc(InterviewRecord::getCreateTime));
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<InterviewRecord> getInterviewDetail(@PathVariable("id") Long id) {
        InterviewRecord record = interviewService.getById(id);
        if (record != null) {
            // Check authorization
            Long userId = UserHolder.getUser().getId();
            if (!record.getUserId().equals(userId)) {
                return Result.error(403, "Forbidden");
            }
        }
        return Result.success(record);
    }

    @GetMapping("/{id}/resume")
    public Result<Map<String, Object>> resumeInterview(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        try {
            return Result.success(interviewService.resumeInterview(id, userId));
        } catch (IllegalArgumentException e) {
            return Result.error(403, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(410, e.getMessage());
        }
    }
}
