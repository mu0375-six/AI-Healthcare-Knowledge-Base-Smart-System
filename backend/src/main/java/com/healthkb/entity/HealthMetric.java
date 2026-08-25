package com.healthkb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("health_metric")
public class HealthMetric {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long profileId;
    private String metricType;
    @TableField("metric_value")
    @JsonProperty("value")
    private Double metricValue;
    private String unit;
    private LocalDateTime recordedAt;
    private String note;
}
