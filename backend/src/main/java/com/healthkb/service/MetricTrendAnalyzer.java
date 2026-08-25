package com.healthkb.service;

import com.healthkb.common.MetricGuide;
import com.healthkb.entity.HealthMetric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 指标趋势分析。
 *
 * <p>原来的实现只比首末两个值，得出「上升 / 下降 / 基本持平」。
 * 但血压血糖真正有意义的信号不是「比上次高」，而是<b>连续几次都超出参考范围</b> ——
 * 单次偏高可能是测量误差或当天状态，连续三次偏高才提示需要复查。
 * 这里把「末尾连续异常次数」单独算出来，既喂给大模型，也直接回给前端展示。
 */
public final class MetricTrendAnalyzer {

    /** 连续异常达到该次数就升级为提醒。 */
    public static final int ALERT_STREAK = 3;

    private MetricTrendAnalyzer() {
    }

    public record Trend(
            String metricType,
            String unit,
            int samples,
            double latest,
            String latestFlag,
            String latestFlagText,
            int consecutiveAbnormal,
            boolean alert,
            String direction,
            String note) {
    }

    /** 按指标类型分组分析，只保留有数据的类型，顺序与首次出现顺序一致。 */
    public static List<Trend> analyze(List<HealthMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return List.of();
        }
        Map<String, List<HealthMetric>> byType = metrics.stream()
                .collect(Collectors.groupingBy(HealthMetric::getMetricType,
                        LinkedHashMap::new, Collectors.toList()));
        List<Trend> out = new ArrayList<>();
        for (Map.Entry<String, List<HealthMetric>> e : byType.entrySet()) {
            out.add(analyzeOne(e.getKey(), e.getValue()));
        }
        return out;
    }

    private static Trend analyzeOne(String type, List<HealthMetric> raw) {
        List<HealthMetric> list = raw.stream()
                .sorted(Comparator.comparing(HealthMetric::getRecordedAt))
                .toList();
        double first = list.get(0).getMetricValue();
        double latest = list.get(list.size() - 1).getMetricValue();
        String flag = MetricGuide.flag(type, latest);

        // 从最近一次往回数，连续多少次不在参考范围内
        int streak = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            String f = MetricGuide.flag(type, list.get(i).getMetricValue());
            if ("high".equals(f) || "low".equals(f)) {
                streak++;
            } else {
                break;
            }
        }

        String direction = direction(type, first, latest, list.size());
        boolean alert = streak >= ALERT_STREAK;
        return new Trend(type, MetricGuide.unitOf(type), list.size(), latest,
                flag, MetricGuide.flagText(flag), streak, alert, direction,
                note(type, list.size(), first, latest, direction, flag, streak, alert));
    }

    /**
     * 变化方向。阈值取参考区间宽度的 5%，而不是固定的 0.05 ——
     * 收缩压差 0.05 mmHg 毫无意义，空腹血糖差 0.05 mmol/L 却值得记一笔。
     */
    private static String direction(String type, double first, double latest, int samples) {
        if (samples < 2) {
            return "unknown";
        }
        MetricGuide.Band band = MetricGuide.band(type);
        double threshold = band == null
                ? Math.max(0.05, Math.abs(first) * 0.02)
                : Math.max(1e-6, (band.high() - band.low()) * 0.05);
        double delta = latest - first;
        if (delta > threshold) {
            return "rising";
        }
        if (delta < -threshold) {
            return "falling";
        }
        return "flat";
    }

    private static String note(String type, int samples, double first, double latest,
                               String direction, String flag, int streak, boolean alert) {
        if (samples < 2) {
            return type + "仅 1 次记录（" + trim(latest) + "，" + MetricGuide.flagText(flag)
                    + "），再记录几次才能看出趋势。";
        }
        String dirText = switch (direction) {
            case "rising" -> "总体上升";
            case "falling" -> "总体下降";
            case "flat" -> "基本持平";
            default -> "变化不明";
        };
        StringBuilder sb = new StringBuilder();
        sb.append(type).append(" 近 ").append(samples).append(" 次由 ").append(trim(first))
                .append(" 到 ").append(trim(latest)).append("，").append(dirText)
                .append("，最近一次").append(MetricGuide.flagText(flag)).append("。");
        if (alert) {
            sb.append("已连续 ").append(streak).append(" 次超出参考范围，建议尽快复查并咨询医生。");
        } else if (streak > 0) {
            sb.append("最近 ").append(streak).append(" 次超出参考范围，留意后续变化。");
        }
        return sb.toString();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100.0) / 100.0);
    }
}
