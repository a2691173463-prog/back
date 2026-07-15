package com.interview.back.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("user_profile")
public class UserProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String skillName;
    private BigDecimal score;
    private String comment;
    private Long sourceInterviewId;
    private Date lastUpdate;
}
