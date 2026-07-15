package com.interview.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.back.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
