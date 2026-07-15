package com.interview.back.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.common.Result;
import com.interview.back.entity.Question;
import com.interview.back.mapper.QuestionMapper;
import com.interview.back.service.QuestionService;
import com.interview.back.service.InterviewService;
import com.interview.back.utils.CacheClient;
import com.interview.back.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    private final CacheClient cacheClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final InterviewService interviewService;

    public QuestionServiceImpl(CacheClient cacheClient, StringRedisTemplate stringRedisTemplate, InterviewService interviewService) {
        this.cacheClient = cacheClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.interviewService = interviewService;
    }

    @Override
    public Result<Question> queryQuestionById(Long id) {
        // 1. 利用 CacheClient 进行缓存穿透防护查询
        Question question = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_QUESTION_KEY,
                id,
                Question.class,
                this::getById,
                RedisConstants.CACHE_QUESTION_TTL,
                TimeUnit.MINUTES
        );

        if (question == null) {
            return Result.error(404, "题目不存在");
        }

        // 2. 在 Redis 中自增浏览量计数
        stringRedisTemplate.opsForValue().increment(RedisConstants.QUESTION_VIEWS_KEY + id);

        return Result.success(question);
    }

    @Override
    public Result<Long> initInterviewByQuestion(Long questionId, Long resumeId, Long userId) {
        // 1. 校验题目是否存在
        Question question = getById(questionId);
        if (question == null) {
            return Result.error(404, "所选题目不存在");
        }

        // 2. 初始化面试记录 (调用已有的 InterviewService，重载传入 questionId)
        Long interviewId = interviewService.initInterview(resumeId, userId, questionId);

        // 3. 在 Redis ZSet 中自增对练量，用于热门排行
        stringRedisTemplate.opsForZSet().incrementScore(
                RedisConstants.RANK_QUESTION_INTERVIEW,
                questionId.toString(),
                1.0
        );

        return Result.success(interviewId);
    }

    @Override
    public Result<List<Question>> queryHotQuestions() {
        // 1. 从 Redis ZSet 查询前 5 名热门对练题目 ID
        Set<String> idSet = stringRedisTemplate.opsForZSet().reverseRange(
                RedisConstants.RANK_QUESTION_INTERVIEW,
                0,
                4
        );

        if (CollUtil.isEmpty(idSet)) {
            // 2. 缓存未命中，从数据库查询对练量排名前 5 的题目
            List<Question> hotQuestions = query()
                    .orderByDesc("interview_count")
                    .last("LIMIT 5")
                    .list();

            if (CollUtil.isEmpty(hotQuestions)) {
                return Result.success(Collections.emptyList());
            }

            // 3. 将其写入 Redis ZSet 作为初始值
            for (Question q : hotQuestions) {
                stringRedisTemplate.opsForZSet().add(
                        RedisConstants.RANK_QUESTION_INTERVIEW,
                        q.getId().toString(),
                        q.getInterviewCount().doubleValue()
                );
            }
            return Result.success(hotQuestions);
        }

        // 4. 解析 ID 列表，并逐个查询题目（走本地缓存 queryQuestionById）
        List<Question> questions = new ArrayList<>();
        for (String idStr : idSet) {
            Long id = Long.valueOf(idStr);
            Result<Question> result = queryQuestionById(id);
            if (result.getCode() == 200 && result.getData() != null) {
                // 注意：从缓存取出的题目，我们需要获取实时的对练分值作为 interviewCount
                Question q = result.getData();
                Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.RANK_QUESTION_INTERVIEW, idStr);
                if (score != null) {
                    q.setInterviewCount(score.intValue());
                }
                questions.add(q);
            }
        }

        return Result.success(questions);
    }
}
