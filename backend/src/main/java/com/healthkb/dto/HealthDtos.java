package com.healthkb.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class HealthDtos {

    @Data
    public static class ProfileRequest {
        private String displayName;
        private String relation;
        private Integer age;
        private String sex;
        private Double heightCm;
        private Double weightKg;
        private String allergies;
        private Boolean sharedToAdmin;
    }

    @Data
    public static class MetricRequest {
        private Long profileId;
        @NotBlank(message = "指标类型不能为空")
        private String metricType;
        @NotNull(message = "指标值不能为空")
        private Double value;
        private String unit;
        private LocalDateTime recordedAt;
        private String note;
    }

    /**
     * CSV / 批量导入：一次请求写多条。items 故意不做条目级 @Valid 级联 ——
     * 导入是宽容语义，个别脏行由服务端跳过并计数，不能让一条坏数据拖垮整批。
     */
    @Data
    public static class MetricBatchRequest {
        private Long profileId;

        @Size(min = 1, max = 500, message = "单次导入 1-500 条指标")
        private List<MetricRequest> items;
    }

    @Data
    public static class HistoryRequest {
        private Long profileId;
        @NotBlank(message = "疾病名称不能为空")
        private String disease;
        private LocalDate diagnosedAt;
        private String status;
        private String note;
    }

    /**
     * 异常提醒项：(档案, 指标类型) 粒度，连续超出参考范围达到阈值才出现。
     * severity 区分「连续超标需复查」（warning）与「偶发异常可观察」（watch）。
     */
    @Data
    public static class AlertItem {
        private Long metricId;
        private Long profileId;
        private String profileName;
        private String metricType;
        private Double latestValue;
        private String unit;
        private String flag;
        private int consecutiveAbnormal;
        private int samples;
        private String refRange;
        private String recordedAt;
        private String severity;
    }
}
