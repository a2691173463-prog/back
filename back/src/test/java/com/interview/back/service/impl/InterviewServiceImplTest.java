package com.interview.back.service.impl;

import com.interview.back.client.AiClient;
import com.interview.back.mapper.ResumeMapper;
import com.interview.back.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InterviewServiceImplTest {

    private final InterviewServiceImpl service = new InterviewServiceImpl(
            mock(ResumeMapper.class),
            mock(AiClient.class),
            mock(StringRedisTemplate.class),
            mock(UserProfileService.class),
            Runnable::run
    );

    @Test
    void extractsOnlyVisibleMessagesFromCheckpointTranscript() {
        Map<String, Object> state = Map.of(
                "transcript", List.of(
                        Map.of("role", "assistant", "content", "问题一"),
                        Map.of("role", "tool", "content", "内部工具结果"),
                        Map.of("role", "user", "content", "回答一")
                ),
                "messages", List.of(Map.of("role", "user", "content", "兼容字段不应优先"))
        );

        List<Map<String, Object>> transcript = ReflectionTestUtils.invokeMethod(
                service,
                "extractTranscript",
                state
        );

        assertThat(transcript)
                .containsExactly(
                        Map.of("role", "assistant", "content", "问题一"),
                        Map.of("role", "user", "content", "回答一")
                );
    }

    @Test
    void fallsBackToCompatibilityMessagesForOlderAgentResponses() {
        Map<String, Object> state = Map.of(
                "messages", List.of(Map.of("role", "assistant", "content", "旧版本记录"))
        );

        List<Map<String, Object>> transcript = ReflectionTestUtils.invokeMethod(
                service,
                "extractTranscript",
                state
        );

        assertThat(transcript)
                .containsExactly(Map.of("role", "assistant", "content", "旧版本记录"));
    }
}
