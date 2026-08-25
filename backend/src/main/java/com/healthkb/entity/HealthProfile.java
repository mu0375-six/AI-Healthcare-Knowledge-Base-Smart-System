package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("health_profile")
public class HealthProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String displayName;
    private String relation;
    private Integer age;
    private String sex;
    private Double heightCm;
    private Double weightKg;
    private String allergies;
    private Boolean sharedToAdmin;
    private String lastAdvice;
    private LocalDateTime adviceAt;
    private LocalDateTime updatedAt;
}
