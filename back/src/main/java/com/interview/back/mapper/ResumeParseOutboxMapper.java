package com.interview.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interview.back.entity.ResumeParseOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

@Mapper
public interface ResumeParseOutboxMapper extends BaseMapper<ResumeParseOutbox> {

    @Update("""
            UPDATE resume_parse_outbox
            SET status = 3, update_time = NOW()
            WHERE id = #{id}
              AND status = 0
              AND next_retry_time <= NOW()
            """)
    int claim(@Param("id") Long id);

    @Update("""
            UPDATE resume_parse_outbox
            SET status = 1, last_error = NULL, update_time = NOW()
            WHERE id = #{id} AND status = 3
            """)
    int markSent(@Param("id") Long id);

    @Update("""
            UPDATE resume_parse_outbox
            SET status = #{status},
                retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime},
                last_error = #{lastError},
                update_time = NOW()
            WHERE id = #{id} AND status = 3
            """)
    int markPublishFailure(
            @Param("id") Long id,
            @Param("status") int status,
            @Param("retryCount") int retryCount,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("lastError") String lastError
    );

    @Select("""
            SELECT id
            FROM resume_parse_outbox
            WHERE status = 0 AND next_retry_time <= NOW()
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> selectPendingIds(@Param("limit") int limit);

    @Update("""
            UPDATE resume_parse_outbox
            SET status = 0, next_retry_time = NOW(), update_time = NOW()
            WHERE status = 3 AND update_time < #{staleBefore}
            """)
    int releaseStaleClaims(@Param("staleBefore") Date staleBefore);
}
