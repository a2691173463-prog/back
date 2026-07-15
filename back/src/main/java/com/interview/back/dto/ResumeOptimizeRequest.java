package com.interview.back.dto;

import lombok.Data;

@Data
public class ResumeOptimizeRequest {
    private String sectionType;
    private String targetRole;
    private String content;
}
