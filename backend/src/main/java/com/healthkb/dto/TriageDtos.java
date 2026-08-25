package com.healthkb.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class TriageDtos {

    @Data
    public static class Request {
        @NotBlank(message = "请描述主要症状")
        private String symptoms;
        private Integer age;
        private String sex;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepartmentHit {
        private String department;
        private double score;
        private String reason;
        private String urgency;
    }

    @Data
    public static class Response {
        private String urgency;
        private String summary;
        private List<DepartmentHit> departments;
        private String disclaimer;
    }
}
