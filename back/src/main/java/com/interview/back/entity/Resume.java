package com.interview.back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("resume")
public class Resume {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String fileName;
    private String fileUrl;
    private String parsedContent;
    private String diagnosisResult;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
