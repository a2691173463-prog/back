package com.interview.back.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.interview.back.common.Result;
import com.interview.back.entity.Question;
import java.util.List;

public interface QuestionService extends IService<Question> {
    Result<Question> queryQuestionById(Long id);
    Result<Long> initInterviewByQuestion(Long questionId, Long resumeId, Long userId);
    Result<List<Question>> queryHotQuestions();
}
