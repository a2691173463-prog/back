package com.interview.back.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface AiClient {
    String chat(List<Map<String, String>> messages);

    String vlChat(List<Map<String, Object>> messages);

    void streamChat(List<Map<String, Object>> messages, SseEmitter emitter);

    void initializeInterviewThread(Long interviewId, Long userId, Long resumeId, String systemPrompt, String greeting);

    void streamInterview(Long interviewId, Long userId, Long resumeId, String message, SseEmitter emitter);

    void deleteInterviewThread(Long interviewId);

    boolean interviewThreadExists(Long interviewId);

    Map<String, Object> getInterviewThreadState(Long interviewId);

    String diagnoseResume(String parsedText, String imageBase64, String imageMimeType);

    String summarizeInterview(String roundContent, String existingSummary, int maxChars);

    String evaluateInterview(List<Map<String, Object>> messages);
}
