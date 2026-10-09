package com.interview.back.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AiClient {
    String chat(List<Map<String, String>> messages);

    String vlChat(List<Map<String, Object>> messages);

    void streamChat(List<Map<String, Object>> messages, SseEmitter emitter);

    void initializeInterviewThread(Long interviewId, Long userId, Long resumeId, String interviewMode,
                                   String positionCode, String systemPrompt, String greeting);

    void streamInterview(Long interviewId, Long userId, Long resumeId, String interviewMode,
                         String positionCode, String message, SseEmitter emitter);

    void deleteInterviewThread(Long interviewId);

    boolean interviewThreadExists(Long interviewId);

    Map<String, Object> getInterviewThreadState(Long interviewId);

    String diagnoseResume(String parsedText, String imageBase64, String imageMimeType);

    CompletableFuture<String> diagnoseResumeAsync(String parsedText, String imageBase64, String imageMimeType);

    String optimizeResumeSection(String sectionType, String targetRole, String content);

    String summarizeInterview(String roundContent, String existingSummary, int maxChars);

    String evaluateInterview(List<Map<String, Object>> messages);

    String finalizeInterview(Long interviewId, Long userId, Long resumeId, String interviewMode,
                             String positionCode);

    Map<String, Object> addKnowledgeDocument(Long ownerUserId, String title, String fileName,
                                             String category, String content, String scope,
                                             String positionCode);

    List<Map<String, Object>> listKnowledgeDocuments(Long ownerUserId, boolean includeShared,
                                                     boolean sharedOnly);

    boolean deleteKnowledgeDocument(String documentId, Long ownerUserId, boolean allowShared);

    Map<String, Object> getKnowledgeRetrievalStats();
}
