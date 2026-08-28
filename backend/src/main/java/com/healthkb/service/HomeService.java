package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.common.MetricGuide;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 汇总指标告警时最多回看这么多条记录（各文档同类型多次刷新也无碍）。 */
    private static final int METRIC_SCAN_LIMIT = 600;
    private static final int MAX_ALERTS = 6;

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

        // 指标相关聚合共用一次扫描：latest 以 (档案, 类型) 为粒度，previous 记录同类型上一条
        List<HealthMetric> all = metricMapper.selectList(new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .orderByDesc(HealthMetric::getRecordedAt)
                .last("LIMIT " + METRIC_SCAN_LIMIT));
        Map<String, HealthMetric> latest = new LinkedHashMap<>();
        Map<String, Double> previous = new HashMap<>();
        for (HealthMetric m : all) {
            String key = (m.getProfileId() == null ? 0L : m.getProfileId()) + ":" + m.getMetricType();
            if (!latest.containsKey(key)) {
                latest.put(key, m);
            } else if (!previous.containsKey(key)) {
                previous.put(key, m.getMetricValue());
            }
        }
        o.setAlerts(latestAlerts(nameOf(profiles), latest, previous));
        o.setSeries(latestSeries(all, latest));
        return o;
    }

    private Map<Long, String> nameOf(List<HealthProfile> profiles) {
        Map<Long, String> nameOf = new HashMap<>();
        for (HealthProfile p : profiles) {
            if (p.getId() != null) {
                nameOf.put(p.getId(), p.getDisplayName() == null || p.getDisplayName().isBlank()
                        ? (p.getRelation() == null ? "档案" : p.getRelation()) : p.getDisplayName());
            }
        }
        return nameOf;
    }

    /** 首页迷你趋势：取「最新记录靠前」的至多 3 类指标，每类保留近 8 个点（时间正序）。 */
    private List<HomeDtos.MetricSeries> latestSeries(List<HealthMetric> all,
                                                     Map<String, HealthMetric> latest) {
        Map<String, List<HealthMetric>> byType = new LinkedHashMap<>();
        for (HealthMetric m : all) {
            byType.computeIfAbsent(m.getMetricType(), k -> new ArrayList<>()).add(m);
        }
        return latest.entrySet().stream()
                .sorted(Comparator.comparing(
                        e -> e.getValue().getRecordedAt() == null ? "" : e.getValue().getRecordedAt().toString(),
                        Comparator.reverseOrder()))
                .limit(3)
                .map(e -> {
                    List<HealthMetric> list = byType.getOrDefault(e.getKey(), List.of());
                    HomeDtos.MetricSeries s = new HomeDtos.MetricSeries();
                    s.setMetricType(e.getKey());
                    s.setUnit(list.isEmpty() || list.get(0).getUnit() == null
                            ? MetricGuide.unitOf(e.getKey())
                            : list.get(0).getUnit());
                    s.setFlag(MetricGuide.flag(e.getKey(), e.getValue().getMetricValue()));
                    List<HealthMetric> tail = new ArrayList<>(list.subList(0, Math.min(8, list.size())));
                    java.util.Collections.reverse(tail); // 时间正序
                    for (HealthMetric m : tail) {
                        HomeDtos.Point p = new HomeDtos.Point();
                        p.setWhen(m.getRecordedAt() == null ? "" : m.getRecordedAt().format(WHEN));
                        p.setValue(m.getMetricValue());
                        s.getPoints().add(p);
                    }
                    return s;
                })
                .toList();
    }

    /**
     * 首页「需要留心」：以 (档案, 指标类型) 为粒度取最新一条，过滤掉正常值，
     * 附上与同类型再上一条的差值（delta）。时间倒序取前 MAX_ALERTS 条。
     */
    private List<HomeDtos.MetricAlert> latestAlerts(Map<Long, String> nameOf,
                                                    Map<String, HealthMetric> latest,
                                                    Map<String, Double> previous) {
        List<HomeDtos.MetricAlert> alerts = new ArrayList<>();
        latest.forEach((key, m) -> {
            String flag = MetricGuide.flag(m.getMetricType(), m.getMetricValue());
            if (!"high".equals(flag) && !"low".equals(flag)) {
                return;
            }
            HomeDtos.MetricAlert a = new HomeDtos.MetricAlert();
            a.setMetricId(m.getId());
            a.setProfileId(m.getProfileId());
            a.setProfileName(nameOf.getOrDefault(m.getProfileId(), "档案"));
            a.setMetricType(m.getMetricType());
            a.setMetricValue(m.getMetricValue());
            a.setUnit(m.getUnit() == null ? MetricGuide.unitOf(m.getMetricType()) : m.getUnit());
            a.setFlag(flag);
            MetricGuide.Band band = MetricGuide.band(m.getMetricType());
            a.setRefRange(band == null ? "" : format(band.low()) + "-" + format(band.high()) + " " + band.unit());
            a.setRecordedAt(m.getRecordedAt() == null ? "" : m.getRecordedAt().format(WHEN));
            Double prev = previous.get(key);
            a.setDelta(prev == null ? null : m.getMetricValue() - prev);
            alerts.add(a);
        });
        alerts.sort(Comparator.comparing(HomeDtos.MetricAlert::getRecordedAt, Comparator.naturalOrder()).reversed());
        return alerts.size() > MAX_ALERTS ? new ArrayList<>(alerts.subList(0, MAX_ALERTS)) : alerts;
    }

    private static String format(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
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
