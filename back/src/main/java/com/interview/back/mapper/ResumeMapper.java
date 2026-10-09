package com.interview.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.back.entity.Resume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {

    @Update("""
            UPDATE resume
            SET status = 3, update_time = NOW()
            WHERE id = #{resumeId} AND status = 0
            """)
    int claimPending(@Param("resumeId") Long resumeId);

    @Update("""
            UPDATE resume
            SET status = 0, update_time = NOW()
            WHERE id = #{resumeId} AND status = 3
            """)
    int releaseForRetry(@Param("resumeId") Long resumeId);
}
