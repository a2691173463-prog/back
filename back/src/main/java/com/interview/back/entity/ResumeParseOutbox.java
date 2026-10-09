package com.interview.back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("resume_parse_outbox")
public class ResumeParseOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private Long resumeId;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryTime;
    private String lastError;
    private Date createTime;
    private Date updateTime;
}
