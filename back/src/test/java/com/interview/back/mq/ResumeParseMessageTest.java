package com.interview.back.mq;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeParseMessageTest {

    @Test
    void nextAttemptKeepsLogicalMessageIdentity() {
        ResumeParseMessage initial = ResumeParseMessage.initial(42L);

        ResumeParseMessage retry = initial.nextAttempt();

        assertThat(retry.messageId()).isEqualTo(initial.messageId());
        assertThat(retry.resumeId()).isEqualTo(42L);
        assertThat(retry.attempt()).isEqualTo(1);
        assertThat(retry.createdAt()).isEqualTo(initial.createdAt());
    }
}
