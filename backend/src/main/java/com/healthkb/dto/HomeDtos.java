package com.healthkb.dto;

import com.healthkb.entity.HealthProfile;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public class HomeDtos {

    @Data
    public static class Overview {
        private long profileCount;
        private long metricCount;
        private long reportCount;
        private long favoriteCount;
        private long sessionCount;
        private List<HealthProfile> profiles = new ArrayList<>();
        private List<SessionBrief> recentSessions = new ArrayList<>();
        private List<ReportBrief> recentReports = new ArrayList<>();
        /** 各档案的最新异常指标（按时间倒序，最多 6 条），首页「需要留心」卡片用。 */
        private List<MetricAlert> alerts = new ArrayList<>();
        /** 最近有值的指标序列（最多 3 类 × 近 8 点），首页迷你趋势图用。 */
        private List<MetricSeries> series = new ArrayList<>();
    }

    @Data
    public static class MetricSeries {
        private String metricType;
        private String unit;
        /** 序列内最新一点的标志（high / low / normal）。 */
        private String flag;
        private List<Point> points = new ArrayList<>();
    }

    @Data
    public static class Point {
        private String when;
        private Double value;
    }

    @Data
    public static class MetricAlert {
        private Long metricId;
        private Long profileId;
        private String profileName;
        private String metricType;
        private Double metricValue;
        private String unit;
        private String flag;
        /** 展示用参考范围，如 "3.9-6.1 mmol/L"；未知类型为空。 */
        private String refRange;
        private String recordedAt;
        /** 与同类型上一条的差值，无历史时为 null。 */
        private Double delta;
    }

    @Data
    public static class SessionBrief {
        private Long id;
        private String title;
        private String updatedAt;
    }

    @Data
    public static class ReportBrief {
        private Long id;
        private String filename;
        private String createdAt;
        private String hint;
    }
}
