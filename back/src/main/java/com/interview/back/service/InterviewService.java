package com.interview.back.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.back.entity.InterviewRecord;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

public interface InterviewService extends IService<InterviewRecord> {
    Long initInterview(Long resumeId, Long userId);
    Long initInterview(Long resumeId, Long userId, Long questionId);
    SseEmitter chat(Long interviewId, String userMessage);
    Map<String, Object> resumeInterview(Long interviewId, Long userId);
    InterviewRecord endInterview(Long interviewId);
}
