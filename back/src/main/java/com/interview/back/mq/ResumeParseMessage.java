package com.interview.back.mq;

import java.time.Instant;
import java.util.UUID;

public record ResumeParseMessage(
        String messageId,
        Long resumeId,
        int attempt,
        long createdAt
) {
    public static ResumeParseMessage initial(Long resumeId) {
        return new ResumeParseMessage(
                UUID.randomUUID().toString(),
                resumeId,
                0,
                Instant.now().toEpochMilli()
        );
    }

    public ResumeParseMessage nextAttempt() {
        return new ResumeParseMessage(messageId, resumeId, attempt + 1, createdAt);
    }
}
