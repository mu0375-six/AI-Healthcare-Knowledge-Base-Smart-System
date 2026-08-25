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
