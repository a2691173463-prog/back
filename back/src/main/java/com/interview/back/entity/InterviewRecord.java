package com.interview.back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("interview_record")
public class InterviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long resumeId;
    private Long questionId;
    private String chatHistory;
    private Integer score;
    private String evaluation;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}

