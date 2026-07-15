package com.interview.back.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.interview.back.client.AiClient;
import com.interview.back.entity.InterviewRecord;
import com.interview.back.entity.Resume;
import com.interview.back.entity.Question;
import com.interview.back.mapper.QuestionMapper;
import com.interview.back.mapper.InterviewRecordMapper;
import com.interview.back.mapper.ResumeMapper;
import com.interview.back.service.InterviewService;
import com.interview.back.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InterviewServiceImpl extends ServiceImpl<InterviewRecordMapper, InterviewRecord> implements InterviewService {

    private final ResumeMapper resumeMapper;
    private final AiClient aiClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final QuestionMapper questionMapper;
    private final UserProfileService userProfileService;
    private final Executor aiInterviewExecutor;

    public InterviewServiceImpl(ResumeMapper resumeMapper, AiClient aiClient, 
                                StringRedisTemplate stringRedisTemplate, QuestionMapper questionMapper,
                                UserProfileService userProfileService, 
                                @org.springframework.beans.factory.annotation.Qualifier("aiInterviewExecutor") Executor aiInterviewExecutor) {
        this.resumeMapper = resumeMapper;
        this.aiClient = aiClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.questionMapper = questionMapper;
        this.userProfileService = userProfileService;
        this.aiInterviewExecutor = aiInterviewExecutor;
    }

    private String getRedisKey(Long interviewId) {
        return "interview:session:" + interviewId;
    }

    @Override
    public Long initInterview(Long resumeId, Long userId) {
        return initInterview(resumeId, userId, null);
    }

    @Override
    public Long initInterview(Long resumeId, Long userId, Long questionId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getStatus() != 1) {
            throw new RuntimeException("Resume not found or not parsed yet.");
        }

        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setResumeId(resumeId);
        record.setQuestionId(questionId);
        record.setStatus(0);
        this.save(record);

        String systemPrompt = "你是一个专业的后端开发面试官。你需要根据候选人的简历向他提问。每次只问一个问题。态度专业，指出不足之处。\n";
        if (questionId != null) {
            Question question = questionMapper.selectById(questionId);
            if (question != null) {
                systemPrompt += "【重要】本次面试为专项题目对练，请重点围绕以下技术题目进行提问 and 考察：\n"
                        + "题目标题：" + question.getTitle() + "\n"
                        + "分类：" + question.getCategory() + "\n"
                        + "题目描述：" + question.getDescription() + "\n"
                        + "参考答案：" + question.getReferenceAnswer() + "\n"
                        + "请在提问时结合这道题目的知识点，引导并考察候选人对该题目的掌握程度。\n";
            }
        }
        systemPrompt += """
                开始面试后，请按需使用工具：
                1. 使用 get_resume_summary 获取候选人的简历、项目经历和技术栈；
                2. 使用 get_user_weak_skills 获取历史薄弱技能并进行针对性追问；
                3. 需要补充某个技术方向的题目时，使用 search_question_bank 检索题库。
                不要向候选人展示工具调用过程或题库参考答案。
                """;

        // 2. 将 System Prompt 保存到独立的 String 缓存中，并写入 full_history
        String systemKey = "interview:system:" + record.getId();
        stringRedisTemplate.opsForValue().set(systemKey, systemPrompt, 2, TimeUnit.HOURS);

        String fullKey = "interview:full:" + record.getId();
        Map<String, Object> sysMsg = MapUtil.<String, Object>builder().put("role", "system").put("content", systemPrompt).build();
        stringRedisTemplate.opsForList().rightPush(fullKey, JSONUtil.toJsonStr(sysMsg));

        // 3. 将 Greeting（你好...）放入活跃 session 和 full_history 中
        Map<String, Object> greetingMsg = MapUtil.<String, Object>builder().put("role", "assistant").put("content", "你好！我是你的面试官。我已经阅读了你的简历，请问你准备好开始面试了吗？").build();
        String greeting = greetingMsg.get("content").toString();
        stringRedisTemplate.opsForList().rightPush(fullKey, JSONUtil.toJsonStr(greetingMsg));

        stringRedisTemplate.expire(fullKey, 2, TimeUnit.HOURS);

        aiClient.initializeInterviewThread(
                record.getId(),
                userId,
                resumeId,
                systemPrompt,
                greeting
        );

        return record.getId();
    }


    @Override
    public SseEmitter chat(Long interviewId, String userMessage) {
        SseEmitter emitter = new SseEmitter(600000L); // 10 minutes timeout
        Long userId = com.interview.back.utils.UserHolder.getUser().getId();
        InterviewRecord record = this.getById(interviewId);
        if (record == null || !record.getUserId().equals(userId) || record.getStatus() != 0) {
            emitter.completeWithError(new RuntimeException("Interview not found or access denied."));
            return emitter;
        }

        Map<String, Object> userMsgObj = MapUtil.<String, Object>builder().put("role", "user").put("content", userMessage).build();

        // Redis 仅保存业务全量记录；Agent 的短期状态由 Python Checkpointer 管理。
        String fullKey = "interview:full:" + interviewId;
        stringRedisTemplate.opsForList().rightPush(fullKey, JSONUtil.toJsonStr(userMsgObj));
        stringRedisTemplate.expire(fullKey, 2, TimeUnit.HOURS);

        int promptTokens = Math.max(1, (int) (userMessage.length() / 1.2));

        SseEmitterWrapper wrappedEmitter = new SseEmitterWrapper(
                emitter,
                stringRedisTemplate,
                userId,
                promptTokens,
                interviewId
        );

        aiInterviewExecutor.execute(() -> {
            try {
                aiClient.streamInterview(
                        interviewId,
                        userId,
                        record.getResumeId(),
                        userMessage,
                        wrappedEmitter
                );
            } catch (Exception e) {
                wrappedEmitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public Map<String, Object> resumeInterview(Long interviewId, Long userId) {
        InterviewRecord record = this.getById(interviewId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new IllegalArgumentException("面试记录不存在或无权访问。");
        }
        if (record.getStatus() == null || record.getStatus() != 0) {
            throw new IllegalStateException("该面试已经结束，不能继续作答。");
        }

        Map<String, Object> agentState = aiClient.getInterviewThreadState(interviewId);
        if (!Boolean.TRUE.equals(agentState.get("exists"))) {
            throw new IllegalStateException("该面试的 Agent 上下文已经过期，请重新发起面试。");
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        Object checkpointMessages = agentState.get("messages");
        if (checkpointMessages instanceof List<?>) {
            for (Object item : (List<?>) checkpointMessages) {
                if (item instanceof Map<?, ?>) {
                    messages.add((Map<String, Object>) item);
                }
            }
        }

        String fullKey = "interview:full:" + interviewId;
        if (messages.isEmpty()) {
            List<String> historyJson = stringRedisTemplate.opsForList().range(fullKey, 0, -1);
            if (historyJson != null) {
                messages = historyJson.stream()
                        .map(json -> (Map<String, Object>) JSONUtil.toBean(json, Map.class))
                        .filter(message -> !"system".equals(message.get("role")))
                        .collect(Collectors.toList());
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalStateException("面试历史记录不可用，请重新发起面试。");
        }

        stringRedisTemplate.expire(fullKey, 2, TimeUnit.HOURS);
        stringRedisTemplate.expire("interview:system:" + interviewId, 2, TimeUnit.HOURS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("interviewId", interviewId);
        result.put("resumeId", record.getResumeId());
        result.put("messages", messages);
        result.put("stage", agentState.get("stage"));
        result.put("questionCount", agentState.get("question_count"));
        result.put("currentSkill", agentState.get("current_skill"));
        result.put("coveredSkills", agentState.get("covered_skills"));
        return result;
    }

    @Override
    public InterviewRecord endInterview(Long interviewId) {
        String fullKey = "interview:full:" + interviewId;
        List<String> historyJson = stringRedisTemplate.opsForList().range(fullKey, 0, -1);
        if (historyJson == null || historyJson.isEmpty()) {
            String redisKey = getRedisKey(interviewId);
            historyJson = stringRedisTemplate.opsForList().range(redisKey, 0, -1);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        if (historyJson != null && !historyJson.isEmpty()) {
            messages = historyJson.stream()
                    .map(json -> (Map<String, Object>) JSONUtil.toBean(json, Map.class))
                    .collect(Collectors.toList());
        } else {
            Map<String, Object> agentState = aiClient.getInterviewThreadState(interviewId);
            Object checkpointMessages = agentState.get("messages");
            if (checkpointMessages instanceof List<?>) {
                for (Object item : (List<?>) checkpointMessages) {
                    if (item instanceof Map<?, ?>) {
                        messages.add((Map<String, Object>) item);
                    }
                }
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalStateException("面试历史记录不可用，无法生成评价。");
        }

        String aiResult = aiClient.evaluateInterview(messages);
        
        if (aiResult.startsWith("```json")) {
            aiResult = aiResult.substring(7, aiResult.length() - 3).trim();
        } else if (aiResult.startsWith("```")) {
            aiResult = aiResult.substring(3, aiResult.length() - 3).trim();
        }

        int score = 60;
        String evaluationText = aiResult;
        List<Map<String, Object>> skills = new ArrayList<>();
        try {
            Map<String, Object> evalMap = JSONUtil.toBean(aiResult, Map.class);
            if (evalMap.containsKey("score")) {
                score = Integer.parseInt(evalMap.get("score").toString());
            }
            if (evalMap.containsKey("evaluation")) {
                evaluationText = evalMap.get("evaluation").toString();
            }
            if (evalMap.containsKey("skills")) {
                skills = (List<Map<String, Object>>) evalMap.get("skills");
            }
        } catch (Exception e) {
            // Ignore parse error
        }

        InterviewRecord record = this.getById(interviewId);
        if (record == null) {
            throw new RuntimeException("Interview record not found.");
        }
        record.setChatHistory(JSONUtil.toJsonStr(messages));
        record.setScore(score);
        record.setEvaluation(evaluationText);
        record.setStatus(1);
        this.updateById(record);
        aiClient.deleteInterviewThread(interviewId);

        // 更新用户能力画像数据，调用 EMA 平滑更新
        Long userId = record.getUserId();
        if (userId != null && skills != null && !skills.isEmpty()) {
            for (Map<String, Object> skill : skills) {
                try {
                    String skillName = (String) skill.get("skillName");
                    double skillScore = Double.parseDouble(skill.get("score").toString());
                    String comment = (String) skill.get("comment");
                    userProfileService.saveOrUpdateSkill(userId, skillName, skillScore, comment, interviewId);
                } catch (Exception ex) {
                    log.error("Failed to parse and save user skill profile for: {}", skill, ex);
                }
            }
        }

        // 清理所有关联 Redis 缓存
        stringRedisTemplate.delete(getRedisKey(interviewId));
        stringRedisTemplate.delete("interview:system:" + interviewId);
        stringRedisTemplate.delete("interview:summary:" + interviewId);
        stringRedisTemplate.delete(fullKey);

        return record;
    }

    private static class SseEmitterWrapper extends SseEmitter {
        private final SseEmitter target;
        private final StringBuilder aiResponse = new StringBuilder();
        private final StringRedisTemplate stringRedisTemplate;
        private final Long userId;
        private final int promptTokens;
        private final Long interviewId;
        private final AtomicBoolean completed = new AtomicBoolean(false);

        public SseEmitterWrapper(SseEmitter target, StringRedisTemplate stringRedisTemplate,
                                 Long userId, int promptTokens, Long interviewId) {
            this.target = target;
            this.stringRedisTemplate = stringRedisTemplate;
            this.userId = userId;
            this.promptTokens = promptTokens;
            this.interviewId = interviewId;
        }

        @Override
        public void send(Object object) throws java.io.IOException {
            if (object instanceof String) {
                String value = (String) object;
                if (JSONUtil.isTypeJSON(value)) {
                    Map<String, Object> event = JSONUtil.toBean(value, Map.class);
                    if ("token".equals(String.valueOf(event.get("type")))) {
                        Object content = event.get("content");
                        if (content != null) {
                            aiResponse.append(content);
                        }
                    }
                } else if (!value.startsWith("[AI_ERROR]")) {
                    aiResponse.append(value);
                }
            }
            target.send(object);
        }

        @Override
        public void complete() {
            if (!completed.compareAndSet(false, true)) {
                return;
            }

            boolean hasResponse = !aiResponse.isEmpty();
            if (hasResponse) {
                Map<String, Object> aiMsgObj = MapUtil.<String, Object>builder()
                        .put("role", "assistant")
                        .put("content", aiResponse.toString())
                        .build();
                String fullKey = "interview:full:" + interviewId;
                stringRedisTemplate.opsForList().rightPush(fullKey, JSONUtil.toJsonStr(aiMsgObj));
                stringRedisTemplate.expire(fullKey, 2, TimeUnit.HOURS);
            }
            
            try {
                if (hasResponse && userId != null) {
                    int responseTokens = (int) (aiResponse.length() / 1.2);
                    int totalTokens = promptTokens + responseTokens;
                    int consumeEp = totalTokens / 10;
                    if (consumeEp < 1) {
                        consumeEp = 1;
                    }
                    
                    String energyKey = com.interview.back.utils.RedisConstants.USER_ENERGY_USED_KEY + userId;
                    Long count = stringRedisTemplate.opsForValue().increment(energyKey, consumeEp);
                    if (count != null && count.equals((long) consumeEp)) {
                        stringRedisTemplate.expire(energyKey, 5, java.util.concurrent.TimeUnit.HOURS);
                    }
                }
            } catch (Exception e) {
                // Ignore billing errors
            }
            
            target.complete();
        }

        @Override
        public void completeWithError(Throwable ex) {
            if (completed.compareAndSet(false, true)) {
                target.completeWithError(ex);
            }
        }
    }
}
