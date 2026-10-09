package com.interview.back.enums;

import java.util.Arrays;

public enum InterviewMode {
    STANDARD("standard"),
    PRACTICE("practice");

    private final String value;

    InterviewMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static InterviewMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("面试模式不能为空。");
        }
        return Arrays.stream(values())
                .filter(mode -> mode.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "不支持的面试模式，请选择 standard 或 practice。"));
    }
}
