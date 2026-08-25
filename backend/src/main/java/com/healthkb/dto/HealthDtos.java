package com.healthkb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Data
    public static class HistoryRequest {
        private Long profileId;
        @NotBlank(message = "疾病名称不能为空")
        private String disease;
        private LocalDate diagnosedAt;
        private String status;
        private String note;
    }
}
