package com.interview.back;

import com.interview.back.enums.InterviewMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InterviewModeTest {

    @Test
    void parsesSupportedModes() {
        assertEquals(InterviewMode.STANDARD, InterviewMode.fromValue("standard"));
        assertEquals(InterviewMode.PRACTICE, InterviewMode.fromValue("PRACTICE"));
    }

    @Test
    void rejectsUnsupportedMode() {
        assertThrows(IllegalArgumentException.class, () -> InterviewMode.fromValue("unknown"));
    }
}
