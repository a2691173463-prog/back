package com.interview.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.back.entity.Resume;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
