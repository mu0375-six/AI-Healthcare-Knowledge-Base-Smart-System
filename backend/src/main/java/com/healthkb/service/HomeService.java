package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.dto.HomeDtos;
import com.healthkb.entity.ChatSession;
import com.healthkb.entity.ExamReport;
import com.healthkb.entity.Favorite;
import com.healthkb.entity.HealthMetric;
import com.healthkb.entity.HealthProfile;
import com.healthkb.mapper.ChatSessionMapper;
import com.healthkb.mapper.ExamReportMapper;
import com.healthkb.mapper.FavoriteMapper;
import com.healthkb.mapper.HealthMetricMapper;
import com.healthkb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final HealthService healthService;
    private final HealthMetricMapper metricMapper;
    private final ExamReportMapper reportMapper;
    private final FavoriteMapper favoriteMapper;
    private final ChatSessionMapper sessionMapper;

    public HomeDtos.Overview overview() {
        Long userId = SecurityUtils.currentUserId();
        List<HealthProfile> profiles = healthService.listProfiles();

        HomeDtos.Overview o = new HomeDtos.Overview();
        o.setProfiles(profiles);
        o.setProfileCount(profiles.size());
        o.setMetricCount(count(metricMapper.selectCount(new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId))));
        o.setReportCount(count(reportMapper.selectCount(new LambdaQueryWrapper<ExamReport>()
                .eq(ExamReport::getUserId, userId))));
        o.setFavoriteCount(count(favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId))));
        o.setSessionCount(count(sessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId))));
        o.setRecentSessions(sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt)
                        .last("LIMIT 5"))
                .stream()
                .map(this::toSession)
                .toList());
        o.setRecentReports(reportMapper.selectList(new LambdaQueryWrapper<ExamReport>()
                        .eq(ExamReport::getUserId, userId)
                        .orderByDesc(ExamReport::getCreatedAt)
                        .last("LIMIT 4"))
                .stream()
                .map(this::toReport)
                .toList());
        return o;
    }

    private HomeDtos.SessionBrief toSession(ChatSession s) {
        HomeDtos.SessionBrief b = new HomeDtos.SessionBrief();
        b.setId(s.getId());
        b.setTitle(s.getTitle() == null || s.getTitle().isBlank() ? "未命名会话" : s.getTitle());
        b.setUpdatedAt(s.getUpdatedAt() == null ? "" : s.getUpdatedAt().format(WHEN));
        return b;
    }

    private HomeDtos.ReportBrief toReport(ExamReport r) {
        HomeDtos.ReportBrief b = new HomeDtos.ReportBrief();
        b.setId(r.getId());
        b.setFilename(r.getFilename());
        b.setCreatedAt(r.getCreatedAt() == null ? "" : r.getCreatedAt().format(WHEN));
        b.setHint(hintOf(r.getSummary()));
        return b;
    }

    private String hintOf(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        String s = summary.replaceAll("[#*_>`]", " ").replaceAll("\\s+", " ").trim();
        s = s.replaceFirst("^您好[，,].{0,40}。\\s*", "").trim();
        return s.length() > 72 ? s.substring(0, 72) + "…" : s;
    }

    private long count(Long n) {
        return n == null ? 0 : n;
    }
}
