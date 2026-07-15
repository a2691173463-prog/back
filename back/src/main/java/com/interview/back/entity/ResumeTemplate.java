package com.interview.back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("resume_template")
public class ResumeTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;
    private String thumbnailUrl;
    private String downloadUrl;
    private String description;
    private Date createTime;
    private Date updateTime;
}
