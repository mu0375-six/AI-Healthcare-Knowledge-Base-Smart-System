package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.healthkb.common.AppException;
import com.healthkb.common.MedicalConstants;
import com.healthkb.common.MetricGuide;
import com.healthkb.entity.ExamReportItem;
import com.healthkb.dto.HealthDtos;
import com.healthkb.entity.HealthHistory;
import com.healthkb.entity.HealthMetric;
import com.healthkb.entity.HealthProfile;
import com.healthkb.mapper.HealthHistoryMapper;
import com.healthkb.mapper.HealthMetricMapper;
import com.healthkb.mapper.HealthProfileMapper;
import com.healthkb.rag.LlmClient;
import com.healthkb.rag.RagService;
import com.healthkb.rag.ScoredChunk;
import com.healthkb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthProfileMapper profileMapper;
    private final HealthMetricMapper metricMapper;
    private final HealthHistoryMapper historyMapper;
    private final RagService ragService;
    private final LlmClient llmClient;

    public List<HealthProfile> listProfiles() {
        Long userId = SecurityUtils.currentUserId();
        ensureProfile(userId);
        return profileMapper.selectList(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId)
                .orderByAsc(HealthProfile::getId));
    }

    public HealthProfile createProfile(HealthDtos.ProfileRequest req) {
        Long userId = SecurityUtils.currentUserId();
        HealthProfile p = new HealthProfile();
        p.setUserId(userId);
        p.setDisplayName(blankTo(req.getDisplayName(), "未命名"));
        p.setRelation(blankTo(req.getRelation(), "其他"));
        applyProfile(p, req);
        p.setSharedToAdmin(Boolean.TRUE.equals(req.getSharedToAdmin()));
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(p);
        return p;
    }

    public HealthProfile updateProfile(Long id, HealthDtos.ProfileRequest req) {
        HealthProfile p = requireOwnedProfile(id);
        applyProfile(p, req);
        if (req.getSharedToAdmin() != null) {
            p.setSharedToAdmin(req.getSharedToAdmin());
        }
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
        return p;
    }

    public void deleteProfile(Long id) {
        HealthProfile p = requireOwnedProfile(id);
        Long userId = SecurityUtils.currentUserId();
        Long n = profileMapper.selectCount(new LambdaQueryWrapper<HealthProfile>().eq(HealthProfile::getUserId, userId));
        if (n != null && n <= 1) {
            throw AppException.badRequest("至少保留一份档案");
        }
        metricMapper.delete(new LambdaQueryWrapper<HealthMetric>().eq(HealthMetric::getProfileId, id));
        historyMapper.delete(new LambdaQueryWrapper<HealthHistory>().eq(HealthHistory::getProfileId, id));
        profileMapper.deleteById(p.getId());
    }

    public HealthProfile getMyProfile() {
        return ensureProfile(SecurityUtils.currentUserId());
    }

    public HealthProfile updateMyProfile(HealthDtos.ProfileRequest req) {
        HealthProfile p = ensureProfile(SecurityUtils.currentUserId());
        applyProfile(p, req);
        if (req.getSharedToAdmin() != null) {
            p.setSharedToAdmin(req.getSharedToAdmin());
        }
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(p);
        return p;
    }

    private void applyProfile(HealthProfile p, HealthDtos.ProfileRequest req) {
        if (req.getDisplayName() != null && !req.getDisplayName().isBlank()) {
            p.setDisplayName(req.getDisplayName().trim());
        }
        if (req.getRelation() != null && !req.getRelation().isBlank()) {
            p.setRelation(req.getRelation().trim());
        }
        if (req.getAge() != null) {
            p.setAge(req.getAge());
        }
        if (req.getSex() != null) {
            p.setSex(req.getSex());
        }
        if (req.getHeightCm() != null) {
            p.setHeightCm(req.getHeightCm());
        }
        if (req.getWeightKg() != null) {
            p.setWeightKg(req.getWeightKg());
        }
        if (req.getAllergies() != null) {
            p.setAllergies(req.getAllergies());
        }
    }

    public List<HealthMetric> listMetrics(Long profileId) {
        HealthProfile p = resolveProfile(profileId);
        return metricMapper.selectList(new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, SecurityUtils.currentUserId())
                .eq(HealthMetric::getProfileId, p.getId())
                .orderByDesc(HealthMetric::getRecordedAt));
    }

    public HealthMetric addMetric(HealthDtos.MetricRequest req) {
        HealthProfile p = resolveProfile(req.getProfileId());
        HealthMetric m = new HealthMetric();
        m.setUserId(SecurityUtils.currentUserId());
        m.setProfileId(p.getId());
        m.setMetricType(req.getMetricType().trim());
        m.setMetricValue(req.getValue());
        m.setUnit(req.getUnit());
        m.setRecordedAt(req.getRecordedAt() == null ? LocalDateTime.now() : req.getRecordedAt());
        m.setNote(req.getNote());
        metricMapper.insert(m);
        return m;
    }

    public void deleteMetric(Long id) {
        HealthMetric m = metricMapper.selectById(id);
        if (m == null || !SecurityUtils.currentUserId().equals(m.getUserId())) {
            throw AppException.notFound("指标不存在");
        }
        metricMapper.deleteById(id);
    }

    public List<HealthHistory> listHistories(Long profileId) {
        HealthProfile p = resolveProfile(profileId);
        return historyMapper.selectList(new LambdaQueryWrapper<HealthHistory>()
                .eq(HealthHistory::getUserId, SecurityUtils.currentUserId())
                .eq(HealthHistory::getProfileId, p.getId())
                .orderByDesc(HealthHistory::getId));
    }

    public HealthHistory addHistory(HealthDtos.HistoryRequest req) {
        HealthProfile p = resolveProfile(req.getProfileId());
        HealthHistory h = new HealthHistory();
        h.setUserId(SecurityUtils.currentUserId());
        h.setProfileId(p.getId());
        h.setDisease(req.getDisease().trim());
        h.setDiagnosedAt(req.getDiagnosedAt());
        h.setStatus(req.getStatus() == null ? "随访中" : req.getStatus());
        h.setNote(req.getNote());
        historyMapper.insert(h);
        return h;
    }

    public void deleteHistory(Long id) {
        HealthHistory h = historyMapper.selectById(id);
        if (h == null || !SecurityUtils.currentUserId().equals(h.getUserId())) {
            throw AppException.notFound("病史不存在");
        }
        historyMapper.deleteById(id);
    }

    public Map<String, String> advice(Long profileId) {
        Long userId = SecurityUtils.currentUserId();
        HealthProfile profile = resolveProfile(profileId);
        List<HealthMetric> metrics = metricMapper.selectList(new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getProfileId, profile.getId())
                .orderByAsc(HealthMetric::getRecordedAt));
        if (metrics.isEmpty()) {
            throw AppException.badRequest("请先为「" + nvl(profile.getDisplayName()) + "」记录至少一条指标");
        }
        List<HealthHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<HealthHistory>()
                .eq(HealthHistory::getUserId, userId)
                .eq(HealthHistory::getProfileId, profile.getId()));

        String basis = buildBasis(profile, metrics, histories);
        String context = buildContext(profile, metrics, histories);
        String query = "健康建议 " + (profile.getAge() == null ? "" : profile.getAge() + "岁 ")
                + joinDiseases(histories) + " " + joinMetricTypes(metrics);
        List<ScoredChunk> chunks = ragService.retrieve(query, 5);
        String extra = context + "\n" + trendComments(metrics);
        String text = llmClient.generateSync("请根据我的健康档案生成生活方式与复查建议", chunks, List.of(), extra);
        profile.setLastAdvice(text);
        profile.setAdviceAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(profile);
        return Map.of(
                "advice", text,
                "basis", basis,
                "generatedAt", profile.getAdviceAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        );
    }

    public String briefForChat(Long profileId) {
        if (profileId == null) {
            return "";
        }
        HealthProfile profile = resolveProfile(profileId);
        Long userId = SecurityUtils.currentUserId();
        List<HealthMetric> metrics = metricMapper.selectList(new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getProfileId, profile.getId())
                .orderByDesc(HealthMetric::getRecordedAt));
        List<HealthHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<HealthHistory>()
                .eq(HealthHistory::getUserId, userId)
                .eq(HealthHistory::getProfileId, profile.getId()));
        return "【当前问诊档案】\n" + buildContext(profile, metrics, histories)
                + "\n请结合这份档案回答，不要编造档案里没有的检查结果。";
    }

    public int importReportItems(Long profileId, List<ExamReportItem> items, String reportName, LocalDateTime when) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        HealthProfile profile = resolveProfile(profileId);
        Set<String> allow = Set.of("空腹血糖", "餐后血糖", "收缩压", "舒张压", "体重", "糖化血红蛋白");
        LocalDateTime at = when == null ? LocalDateTime.now() : when;
        String note = "来自报告「" + (reportName == null ? "体检" : reportName) + "」";
        int n = 0;
        for (ExamReportItem item : items) {
            if (item.getName() == null || !allow.contains(item.getName().trim())) {
                continue;
            }
            Double v = parseDouble(item.getItemValue());
            if (v == null) {
                continue;
            }
            HealthMetric m = new HealthMetric();
            m.setUserId(SecurityUtils.currentUserId());
            m.setProfileId(profile.getId());
            m.setMetricType(item.getName().trim());
            m.setMetricValue(v);
            String unit = item.getUnit();
            if (unit == null || unit.isBlank()) {
                unit = MetricGuide.unitOf(item.getName());
            }
            m.setUnit(unit);
            m.setRecordedAt(at);
            m.setNote(note);

            // 幂等：上传时选了档案会自动导入一次，报告详情页的「写入档案」按钮会再调一次。
            // 同一份报告的同一指标只应留一条，否则档案里会出现重复记录、且「较上次」恒为 0。
            HealthMetric exists = metricMapper.selectOne(new LambdaQueryWrapper<HealthMetric>()
                    .eq(HealthMetric::getProfileId, profile.getId())
                    .eq(HealthMetric::getMetricType, m.getMetricType())
                    .eq(HealthMetric::getRecordedAt, at)
                    .eq(HealthMetric::getNote, note)
                    .last("LIMIT 1"));
            if (exists != null) {
                exists.setMetricValue(v);
                exists.setUnit(unit);
                metricMapper.updateById(exists);
            } else {
                metricMapper.insert(m);
            }
            n++;
        }
        return n;
    }

    private String buildBasis(HealthProfile p, List<HealthMetric> metrics, List<HealthHistory> histories) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据「").append(nvl(p.getDisplayName())).append("」");
        if (p.getAge() != null) {
            sb.append("，").append(p.getAge()).append("岁");
        }
        Double bmi = MetricGuide.bmi(p.getHeightCm(), p.getWeightKg());
        if (bmi != null) {
            sb.append("，BMI ").append(String.format("%.1f", bmi)).append("（").append(MetricGuide.bmiLabel(bmi)).append("）");
        }
        Map<String, HealthMetric> latest = new LinkedHashMap<>();
        for (HealthMetric m : metrics) {
            latest.put(m.getMetricType(), m);
        }
        if (!latest.isEmpty()) {
            sb.append("。最近指标：");
            List<String> bits = new ArrayList<>();
            for (HealthMetric m : latest.values()) {
                String flag = MetricGuide.flagText(MetricGuide.flag(m.getMetricType(), m.getMetricValue()));
                bits.add(m.getMetricType() + " " + m.getMetricValue() + nvl(m.getUnit()) + "（" + flag + "）");
            }
            sb.append(String.join("，", bits));
        }
        if (!histories.isEmpty()) {
            sb.append("。病史：").append(joinDiseases(histories));
        }
        return sb.append("。").toString();
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map<String, Object> adminView(Long userId) {
        if (!SecurityUtils.isAdmin()) {
            throw AppException.forbidden("仅管理员可访问");
        }
        HealthProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId)
                .eq(HealthProfile::getSharedToAdmin, true)
                .last("LIMIT 1"));
        if (profile == null) {
            throw AppException.forbidden("该用户未授权管理员查看健康档案");
        }
        List<HealthMetric> metrics = metricMapper.selectList(new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getProfileId, profile.getId())
                .orderByDesc(HealthMetric::getRecordedAt));
        List<HealthHistory> histories = historyMapper.selectList(new LambdaQueryWrapper<HealthHistory>()
                .eq(HealthHistory::getUserId, userId)
                .eq(HealthHistory::getProfileId, profile.getId()));
        return Map.of("profile", profile, "metrics", metrics, "histories", histories);
    }

    private HealthProfile resolveProfile(Long profileId) {
        if (profileId == null) {
            return ensureProfile(SecurityUtils.currentUserId());
        }
        return requireOwnedProfile(profileId);
    }

    private HealthProfile requireOwnedProfile(Long id) {
        HealthProfile p = profileMapper.selectById(id);
        if (p == null || !SecurityUtils.currentUserId().equals(p.getUserId())) {
            throw AppException.notFound("档案不存在");
        }
        return p;
    }

    private HealthProfile ensureProfile(Long userId) {
        List<HealthProfile> all = profileMapper.selectList(new LambdaQueryWrapper<HealthProfile>()
                .eq(HealthProfile::getUserId, userId)
                .orderByAsc(HealthProfile::getId));
        if (!all.isEmpty()) {
            HealthProfile first = all.get(0);
            for (HealthProfile p : all) {
                boolean dirty = false;
                if (p.getDisplayName() == null || p.getDisplayName().isBlank()) {
                    p.setDisplayName("本人".equals(p.getRelation()) || p.getRelation() == null ? "我" : p.getRelation());
                    dirty = true;
                }
                if (p.getRelation() == null || p.getRelation().isBlank()) {
                    p.setRelation("本人");
                    dirty = true;
                }
                if (dirty) {
                    p.setUpdatedAt(LocalDateTime.now());
                    profileMapper.updateById(p);
                }
            }
            backfillOrphans(userId, first.getId());
            return all.stream().filter(x -> "本人".equals(x.getRelation())).findFirst().orElse(first);
        }
        HealthProfile p = new HealthProfile();
        p.setUserId(userId);
        p.setDisplayName("我");
        p.setRelation("本人");
        p.setSharedToAdmin(false);
        p.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(p);
        backfillOrphans(userId, p.getId());
        return p;
    }

    private void backfillOrphans(Long userId, Long profileId) {
        metricMapper.update(null, new LambdaUpdateWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .isNull(HealthMetric::getProfileId)
                .set(HealthMetric::getProfileId, profileId));
        historyMapper.update(null, new LambdaUpdateWrapper<HealthHistory>()
                .eq(HealthHistory::getUserId, userId)
                .isNull(HealthHistory::getProfileId)
                .set(HealthHistory::getProfileId, profileId));
    }

    private static String blankTo(String v, String dft) {
        return v == null || v.isBlank() ? dft : v.trim();
    }

    private String buildContext(HealthProfile p, List<HealthMetric> metrics, List<HealthHistory> histories) {
        StringBuilder sb = new StringBuilder();
        sb.append("档案：").append(nvl(p.getDisplayName())).append("（").append(nvl(p.getRelation())).append("）")
                .append("，年龄=").append(p.getAge())
                .append("，性别=").append(p.getSex())
                .append("，身高cm=").append(p.getHeightCm())
                .append("，体重kg=").append(p.getWeightKg());
        if (p.getHeightCm() != null && p.getWeightKg() != null && p.getHeightCm() > 0) {
            double bmi = p.getWeightKg() / Math.pow(p.getHeightCm() / 100.0, 2);
            sb.append("，BMI=").append(String.format("%.1f", bmi));
        }
        sb.append("，过敏史=").append(p.getAllergies() == null || p.getAllergies().isBlank() ? "未填写" : p.getAllergies());
        sb.append("\n病史：");
        if (histories.isEmpty()) {
            sb.append("无");
        } else {
            sb.append(histories.stream()
                    .map(h -> h.getDisease() + "(" + nvl(h.getStatus()) + ")")
                    .collect(Collectors.joining("；")));
        }
        sb.append("\n指标：");
        if (metrics.isEmpty()) {
            sb.append("暂无");
        } else {
            for (HealthMetric m : metrics) {
                sb.append(m.getMetricType()).append("=").append(m.getMetricValue())
                        .append(nvl(m.getUnit())).append("@").append(m.getRecordedAt()).append("；");
            }
        }
        return sb.toString();
    }

    /**
     * 给大模型看的趋势摘要。分析逻辑在 {@link MetricTrendAnalyzer}，
     * 与 /api/health/trends 返回给前端的是同一份判断，避免两处口径不一致。
     */
    private String trendComments(List<HealthMetric> metrics) {
        List<MetricTrendAnalyzer.Trend> trends = MetricTrendAnalyzer.analyze(metrics);
        StringBuilder sb = new StringBuilder("趋势分析：\n");
        if (trends.isEmpty()) {
            sb.append("暂无指标记录。\n");
        }
        for (MetricTrendAnalyzer.Trend t : trends) {
            sb.append("- ").append(t.note()).append("\n");
        }
        boolean anyAlert = trends.stream().anyMatch(MetricTrendAnalyzer.Trend::alert);
        if (anyAlert) {
            sb.append("注意：上述带「连续超出参考范围」的指标请在建议里重点说明复查安排。\n");
        }
        sb.append(MedicalConstants.DISCLAIMER);
        return sb.toString();
    }

    /** 供 /api/health/trends 直接返回。 */
    public List<MetricTrendAnalyzer.Trend> trends(Long profileId) {
        Long userId = SecurityUtils.currentUserId();
        HealthProfile profile = resolveProfile(profileId);
        List<HealthMetric> metrics = metricMapper.selectList(new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getProfileId, profile.getId())
                .orderByAsc(HealthMetric::getRecordedAt));
        return MetricTrendAnalyzer.analyze(metrics);
    }

    private static String joinDiseases(List<HealthHistory> list) {
        return list.stream().map(HealthHistory::getDisease).collect(Collectors.joining(" "));
    }

    private static String joinMetricTypes(List<HealthMetric> list) {
        return list.stream().map(HealthMetric::getMetricType).distinct().collect(Collectors.joining(" "));
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
