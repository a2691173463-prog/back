package com.interview.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.back.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
