package com.interview.back.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interview.back.config.AiProviderConfig;
import com.interview.back.entity.Question;
import com.interview.back.entity.Resume;
import com.interview.back.entity.UserProfile;
import com.interview.back.mapper.QuestionMapper;
import com.interview.back.mapper.ResumeMapper;
import com.interview.back.service.UserProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/agent")
public class AgentInternalController {

    private final ResumeMapper resumeMapper;
    private final QuestionMapper questionMapper;
    private final UserProfileService userProfileService;
    private final AiProviderConfig aiProviderConfig;

    public AgentInternalController(ResumeMapper resumeMapper,
                                   QuestionMapper questionMapper,
                                   UserProfileService userProfileService,
                                   AiProviderConfig aiProviderConfig) {
        this.resumeMapper = resumeMapper;
        this.questionMapper = questionMapper;
        this.userProfileService = userProfileService;
        this.aiProviderConfig = aiProviderConfig;
    }

    @GetMapping("/resumes/{resumeId}/summary")
    public Map<String, Object> resumeSummary(
            @RequestHeader("X-Agent-Secret") String secret,
            @PathVariable Long resumeId) {
        verifySecret(secret);
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new ResourceNotFoundException();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resumeId", resume.getId());
        result.put("fileName", resume.getFileName());
        result.put("content", limit(resume.getParsedContent(), 12000));
        result.put("diagnosis", limit(resume.getDiagnosisResult(), 4000));
        return result;
    }

    @GetMapping("/users/{userId}/weak-skills")
    public List<Map<String, Object>> weakSkills(
            @RequestHeader("X-Agent-Secret") String secret,
            @PathVariable Long userId) {
        verifySecret(secret);
        return userProfileService.getTopKSkills(userId).stream()
                .map(this::toSkillMap)
                .toList();
    }

    @GetMapping("/questions/search")
    public List<Map<String, Object>> searchQuestions(
            @RequestHeader("X-Agent-Secret") String secret,
            @RequestParam String keyword) {
        verifySecret(secret);
        String value = keyword == null ? "" : keyword.trim();
        return questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .and(wrapper -> wrapper
                                .like(Question::getTitle, value)
                                .or()
                                .like(Question::getCategory, value)
                                .or()
                                .like(Question::getDescription, value))
                        .last("LIMIT 5"))
                .stream()
                .map(this::toQuestionMap)
                .toList();
    }

    private Map<String, Object> toSkillMap(UserProfile skill) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillName", skill.getSkillName());
        result.put("score", skill.getScore());
        result.put("comment", skill.getComment());
        result.put("lastUpdate", skill.getLastUpdate());
        return result;
    }

    private Map<String, Object> toQuestionMap(Question question) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", question.getId());
        result.put("title", question.getTitle());
        result.put("category", question.getCategory());
        result.put("difficulty", question.getDifficulty());
        result.put("description", question.getDescription());
        result.put("referenceAnswer", question.getReferenceAnswer());
        return result;
    }

    private void verifySecret(String secret) {
        if (!aiProviderConfig.getInternalSecret().equals(secret)) {
            throw new UnauthorizedAgentRequestException();
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class UnauthorizedAgentRequestException extends RuntimeException {
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class ResourceNotFoundException extends RuntimeException {
    }
}
