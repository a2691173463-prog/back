package com.interview.back.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.interview.back.entity.Question;
import com.interview.back.service.QuestionService;
import com.interview.back.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class QuestionStatSyncTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final QuestionService questionService;

    public QuestionStatSyncTask(StringRedisTemplate stringRedisTemplate, QuestionService questionService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.questionService = questionService;
    }

    /**
     * 每 1 分钟执行一次，将 Redis 中的浏览量与面试对练数同步回 MySQL (写合并)
     */
    @Scheduled(cron = "0 0/1 * * * ?")
    public void syncQuestionStats() {
        log.info("开始同步题库浏览量与对练量数据到数据库...");
        Map<Long, Question> updateMap = new HashMap<>();

        // 1. 同步浏览量 (String 类型)
        Set<String> keys = stringRedisTemplate.keys(RedisConstants.QUESTION_VIEWS_KEY + "*");
        if (CollUtil.isNotEmpty(keys)) {
            for (String key : keys) {
                try {
                    Long questionId = Long.valueOf(key.substring(RedisConstants.QUESTION_VIEWS_KEY.length()));
                    String val = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(val)) {
                        int views = Integer.parseInt(val);
                        Question q = updateMap.computeIfAbsent(questionId, id -> {
                            Question newQ = new Question();
                            newQ.setId(id);
                            return newQ;
                        });
                        q.setViewCount(views);
                    }
                } catch (Exception e) {
                    log.error("同步浏览量 Key 异常: {}", key, e);
                }
            }
        }

        // 2. 同步对练量 (ZSet 类型)
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().rangeWithScores(
                RedisConstants.RANK_QUESTION_INTERVIEW,
                0,
                -1
        );
        if (CollUtil.isNotEmpty(typedTuples)) {
            for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                try {
                    if (tuple.getValue() != null && tuple.getScore() != null) {
                        Long questionId = Long.valueOf(tuple.getValue());
                        int count = tuple.getScore().intValue();
                        Question q = updateMap.computeIfAbsent(questionId, id -> {
                            Question newQ = new Question();
                            newQ.setId(id);
                            return newQ;
                        });
                        q.setInterviewCount(count);
                    }
                } catch (Exception e) {
                    log.error("同步对练量 Tuple 异常: {}", tuple, e);
                }
            }
        }

        // 3. 批量更新到数据库
        if (CollUtil.isNotEmpty(updateMap.values())) {
            boolean success = questionService.updateBatchById(updateMap.values());
            if (success) {
                log.info("成功同步 {} 条题库统计数据到数据库。", updateMap.size());
            } else {
                log.error("同步题库数据失败！");
            }
        } else {
            log.info("没有需要同步的题库统计数据。");
        }
    }
}
