package com.interview.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.back.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
