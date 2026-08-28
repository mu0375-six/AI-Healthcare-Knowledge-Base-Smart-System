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

    /** 附近医疗资源检索：坐标优先，无坐标则地理编码 address。 */
    @Data
    public static class NearbyRequest {
        private String symptoms;
        /** 导诊结果的首选科室，喂给 LLM 生成贴合症状的推荐说明。 */
        private String department;
        /** emergency / outpatient / self_care，急诊级别时提醒直奔急诊。 */
        private String urgency;
        private Double lng;
        private Double lat;
        private String address;
        /** 勾选后才把位置保存到账户。 */
        private Boolean save;
    }

    @Data
    public static class NearbyPoi {
        private String name;
        private String address;
        private String tel;
        /** 距用户坐标的直线距离，米。 */
        private Double distanceMeters;
        private String typeLabel;
    }

    @Data
    public static class NearbyResponse {
        private String locationLabel;
        /** LLM 生成的推荐说明；LLM 不可用时为兜底文案（adviceSource=template）。 */
        private String advice;
        /** llm / template */
        private String adviceSource;
        private List<NearbyPoi> hospitals;
        private List<NearbyPoi> pharmacies;
    }

    @Data
    public static class SavedLocation {
        private String addressText;
        private Double lng;
        private Double lat;
        private String savedAt;
    }
}
