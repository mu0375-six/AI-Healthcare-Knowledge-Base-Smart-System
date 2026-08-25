package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("health_history")
public class HealthHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long profileId;
    private String disease;
    private LocalDate diagnosedAt;
    private String status;
    private String note;
}
