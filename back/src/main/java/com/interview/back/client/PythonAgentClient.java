package com.interview.back.client;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.back.config.AiProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component("pythonAgentClient")
public class PythonAgentClient implements AiClient {

    private final AiProviderConfig aiProviderConfig;
    private final ObjectMapper objectMapper;

    public PythonAgentClient(AiProviderConfig aiProviderConfig, ObjectMapper objectMapper) {
        this.aiProviderConfig = aiProviderConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);
        return postForContent("/llm/chat", body);
    }

    @Override
    public String vlChat(List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);
        return postForContent("/llm/vision", body);
    }

    public String diagnoseResume(String parsedText, String imageBase64, String imageMimeType) {
        Map<String, Object> body = new HashMap<>();
        body.put("parsed_text", parsedText == null ? "" : parsedText);
        body.put("image_base64", imageBase64);
        body.put("image_mime_type", imageMimeType == null ? "image/png" : imageMimeType);
        return postForContent("/resume/diagnose", body);
    }

    public String summarizeInterview(String roundContent, String existingSummary, int maxChars) {
        Map<String, Object> body = new HashMap<>();
        body.put("round_content", roundContent == null ? "" : roundContent);
        body.put("existing_summary", existingSummary);
        body.put("max_chars", maxChars);
        return postForContent("/interview/summarize", body);
    }

    @Override
    public String evaluateInterview(List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);
        return postForContent("/interview/evaluate", body);
    }

    @Override
    public void streamChat(List<Map<String, Object>> messages, SseEmitter emitter) {
        Map<String, Object> body = new HashMap<>();
        body.put("messages", messages);
        String requestBody = JSONUtil.toJsonStr(body);
        log.info("Calling Python agent stream, messageCount={}, bodyLength={}", messages == null ? 0 : messages.size(), requestBody.length());

        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest.post(endpoint("/llm/chat/stream"))
                .timeout(aiProviderConfig.getPythonAgent().getTimeoutSeconds() * 1000)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .body(requestBody)
                .executeAsync();
             BufferedReader reader = new BufferedReader(new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {

            if (!response.isOk()) {
                String errorBody = reader.lines().collect(Collectors.joining("\n"));
                String errorMessage = "Python agent stream error: " + response.getStatus() + " " + errorBody;
                log.error(errorMessage);
                sendStreamError(emitter, errorMessage);
                return;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6);
                try {
                    if ("[DONE]".equals(data.trim())) {
                        emitter.complete();
                        return;
                    }
                    emitter.send(data);
                } catch (Exception e) {
                    log.error("Failed to forward Python agent stream data", e);
                    sendStreamError(emitter, "Failed to forward Python agent stream data: " + e.getMessage());
                    return;
                }
            }
            emitter.complete();
        } catch (Exception e) {
            log.error("Python agent stream request failed", e);
            sendStreamError(emitter, "Python agent stream request failed: " + e.getMessage());
        }
    }

    @Override
    public void initializeInterviewThread(Long interviewId, Long userId, Long resumeId,
                                          String systemPrompt, String greeting) {
        Map<String, Object> body = new HashMap<>();
        body.put("thread_id", interviewId.toString());
        body.put("user_id", userId);
        body.put("resume_id", resumeId);
        body.put("system_prompt", systemPrompt);
        body.put("greeting", greeting);
        post("/agent/threads", body);
    }

    @Override
    public void streamInterview(Long interviewId, Long userId, Long resumeId,
                                String message, SseEmitter emitter) {
        Map<String, Object> body = new HashMap<>();
        body.put("thread_id", interviewId.toString());
        body.put("user_id", userId);
        body.put("resume_id", resumeId);
        body.put("message", message);
        streamRequest("/agent/interview/stream", body, emitter);
    }

    @Override
    public void deleteInterviewThread(Long interviewId) {
        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest
                .delete(endpoint("/agent/threads/" + interviewId))
                .timeout(aiProviderConfig.getPythonAgent().getTimeoutSeconds() * 1000)
                .execute()) {
            if (!response.isOk()) {
                log.warn("Failed to delete Python agent thread {}, status={}",
                        interviewId, response.getStatus());
            }
        } catch (Exception e) {
            log.warn("Failed to delete Python agent thread {}", interviewId, e);
        }
    }

    @Override
    public boolean interviewThreadExists(Long interviewId) {
        return Boolean.TRUE.equals(getInterviewThreadState(interviewId).get("exists"));
    }

    @Override
    public Map<String, Object> getInterviewThreadState(Long interviewId) {
        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest
                .get(endpoint("/agent/threads/" + interviewId))
                .timeout(aiProviderConfig.getPythonAgent().getTimeoutSeconds() * 1000)
                .execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException(
                        "Python agent returned " + response.getStatus() + ": " + response.body());
            }
            return objectMapper.readValue(
                    response.body(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect Python agent thread " + interviewId, e);
        }
    }

    private void streamRequest(String path, Map<String, Object> body, SseEmitter emitter) {
        String requestBody = JSONUtil.toJsonStr(body);
        log.info("Calling Python agent stream, path={}, bodyLength={}", path, requestBody.length());

        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest.post(endpoint(path))
                .timeout(aiProviderConfig.getPythonAgent().getTimeoutSeconds() * 1000)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "text/event-stream")
                .body(requestBody)
                .executeAsync();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(response.bodyStream(), StandardCharsets.UTF_8))) {
            if (!response.isOk()) {
                String errorBody = reader.lines().collect(Collectors.joining("\n"));
                sendStreamError(emitter, "Python agent stream error: "
                        + response.getStatus() + " " + errorBody);
                return;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6);
                if ("[DONE]".equals(data.trim())) {
                    emitter.complete();
                    return;
                }
                if (JSONUtil.isTypeJSON(data)) {
                    Map<String, Object> event = JSONUtil.toBean(data, Map.class);
                    String type = String.valueOf(event.get("type"));
                    if ("done".equals(type)) {
                        emitter.complete();
                        return;
                    }
                    if ("error".equals(type)) {
                        log.error("Python agent stream error: {}", event.get("message"));
                    }
                }
                emitter.send(data);
            }
            emitter.complete();
        } catch (Exception e) {
            log.error("Python agent stream request failed, path={}", path, e);
            sendStreamError(emitter, "Python agent stream request failed: " + e.getMessage());
        }
    }

    private void sendStreamError(SseEmitter emitter, String message) {
        try {
            emitter.send("[AI_ERROR] " + message);
            emitter.complete();
        } catch (Exception sendError) {
            emitter.completeWithError(sendError);
        }
    }

    private String postForContent(String path, Map<String, Object> body) {
        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest.post(endpoint(path))
                .timeout(aiProviderConfig.getPythonAgent().getTimeoutSeconds() * 1000)
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(body))
                .execute()) {

            if (!response.isOk()) {
                log.error("Python agent error, path={}, status={}, body={}", path, response.getStatus(), response.body());
                return "请求大模型失败，请稍后再试。";
            }
            Map<String, Object> resMap = JSONUtil.toBean(response.body(), Map.class);
            Object content = resMap.get("content");
            return content == null ? "" : content.toString();
        } catch (Exception e) {
            log.error("Failed to call Python agent, path={}", path, e);
            return "请求大模型失败，请稍后再试。";
        }
    }

    private void post(String path, Map<String, Object> body) {
        try (cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest.post(endpoint(path))
                .timeout(aiProviderConfig.getPythonAgent().getTimeoutSeconds() * 1000)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(JSONUtil.toJsonStr(body))
                .execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException(
                        "Python agent returned " + response.getStatus() + ": " + response.body());
            }
        }
    }

    private String endpoint(String path) {
        String baseUrl = aiProviderConfig.getPythonAgent().getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }
}
