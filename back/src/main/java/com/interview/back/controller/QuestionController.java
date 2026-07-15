package com.interview.back.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.interview.back.common.Result;
import com.interview.back.entity.Question;
import com.interview.back.service.QuestionService;
import com.interview.back.utils.RedisConstants;
import com.interview.back.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class QuestionController {

    private final QuestionService questionService;
    private final StringRedisTemplate stringRedisTemplate;

    public QuestionController(QuestionService questionService, StringRedisTemplate stringRedisTemplate) {
        this.questionService = questionService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 用户端公开接口 ====================

    /**
     * 分页查询题目列表 (支持按分类、难度、关键词检索)
     */
    @GetMapping("/questions/list")
    public Result<Page<Question>> listQuestions(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {

        Page<Question> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(StrUtil.isNotBlank(category), Question::getCategory, category)
                .eq(StrUtil.isNotBlank(difficulty), Question::getDifficulty, difficulty)
                .and(StrUtil.isNotBlank(keyword), w -> w.like(Question::getTitle, keyword)
                        .or().like(Question::getDescription, keyword));

        queryWrapper.orderByDesc(Question::getCreateTime);
        questionService.page(pageInfo, queryWrapper);
        return Result.success(pageInfo);
    }

    /**
     * 获取题目详情 (走 Redis 缓存)
     */
    @GetMapping("/questions/{id}")
    public Result<Question> getQuestionById(@PathVariable("id") Long id) {
        return questionService.queryQuestionById(id);
    }

    /**
     * 获取热门面试题排行
     */
    @GetMapping("/questions/hot")
    public Result<List<Question>> getHotQuestions() {
        return questionService.queryHotQuestions();
    }

    /**
     * 针对该题目初始化模拟面试 (需登录)
     */
    @PostMapping("/questions/init-interview")
    public Result<Long> initInterviewByQuestion(
            @RequestParam("questionId") Long questionId,
            @RequestParam("resumeId") Long resumeId) {
        
        Long userId = UserHolder.getUser().getId();
        return questionService.initInterviewByQuestion(questionId, resumeId, userId);
    }

    // ==================== 管理员管理接口 (被 AdminInterceptor 拦截) ====================

    /**
     * 新增面试题
     */
    @PostMapping("/admin/questions")
    public Result<String> addQuestion(@RequestBody Question question) {
        if (StrUtil.isBlank(question.getTitle()) || StrUtil.isBlank(question.getDescription()) 
                || StrUtil.isBlank(question.getReferenceAnswer())) {
            return Result.error(400, "题目标题、内容和参考答案不能为空");
        }
        questionService.save(question);
        return Result.success("题目添加成功");
    }

    /**
     * 修改面试题 (双写一致性：先更新 MySQL，再删除 Redis 缓存)
     */
    @PutMapping("/admin/questions")
    public Result<String> updateQuestion(@RequestBody Question question) {
        if (question.getId() == null) {
            return Result.error(400, "题目 ID 不能为空");
        }
        // 1. 更新 MySQL
        questionService.updateById(question);
        // 2. 清除 Redis 缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_QUESTION_KEY + question.getId());
        return Result.success("题目修改成功");
    }

    /**
     * 删除面试题
     */
    @DeleteMapping("/admin/questions/{id}")
    public Result<String> deleteQuestion(@PathVariable("id") Long id) {
        // 1. 删除 MySQL 中的数据
        questionService.removeById(id);
        // 2. 清除 Redis 中的详情缓存和排行统计
        stringRedisTemplate.delete(RedisConstants.CACHE_QUESTION_KEY + id);
        stringRedisTemplate.opsForZSet().remove(RedisConstants.RANK_QUESTION_INTERVIEW, id.toString());
        return Result.success("题目删除成功");
    }
}
