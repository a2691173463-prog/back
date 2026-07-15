package com.interview.back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String category;
    private String difficulty;
    private String description;
    private String referenceAnswer;
    private Integer viewCount;
    private Integer interviewCount;
    private Date createTime;
    private Date updateTime;
}
