package com.healthkb;

import com.healthkb.entity.HealthMetric;
import com.healthkb.service.MetricTrendAnalyzer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricTrendAnalyzerTest {

    private static HealthMetric m(String type, double value, int dayOffset) {
        HealthMetric h = new HealthMetric();
        h.setMetricType(type);
        h.setMetricValue(value);
        h.setRecordedAt(LocalDateTime.of(2026, 1, 1, 8, 0).plusDays(dayOffset));
        return h;
    }

    @Test
    void threeConsecutiveHighReadingsRaiseAlert() {
        // 收缩压参考上限 139
        List<MetricTrendAnalyzer.Trend> trends = MetricTrendAnalyzer.analyze(List.of(
                m("收缩压", 128, 0), m("收缩压", 145, 1), m("收缩压", 150, 2), m("收缩压", 148, 3)));
        assertEquals(1, trends.size());
        MetricTrendAnalyzer.Trend t = trends.get(0);
        assertEquals(3, t.consecutiveAbnormal());
        assertTrue(t.alert(), "连续 3 次偏高应升级为提醒");
        assertEquals("high", t.latestFlag());
        assertTrue(t.note().contains("连续 3 次"), "结论里要说清连续次数，实际: " + t.note());
    }

    @Test
    void oneAbnormalReadingAmongNormalsDoesNotAlert() {
        // 单次偏高更可能是测量误差，不该直接报警
        List<MetricTrendAnalyzer.Trend> trends = MetricTrendAnalyzer.analyze(List.of(
                m("收缩压", 150, 0), m("收缩压", 120, 1), m("收缩压", 118, 2)));
        MetricTrendAnalyzer.Trend t = trends.get(0);
        assertEquals(0, t.consecutiveAbnormal(), "最近一次正常，连续异常应清零");
        assertFalse(t.alert());
    }

    @Test
    void streakCountsOnlyFromTheLatestBackwards() {
        // 中间正常会打断连续计数
        List<MetricTrendAnalyzer.Trend> trends = MetricTrendAnalyzer.analyze(List.of(
                m("空腹血糖", 8.0, 0), m("空腹血糖", 8.5, 1), m("空腹血糖", 5.2, 2), m("空腹血糖", 7.0, 3)));
        assertEquals(1, trends.get(0).consecutiveAbnormal());
        assertFalse(trends.get(0).alert());
    }

    @Test
    void directionThresholdScalesWithReferenceRange() {
        // 收缩压参考区间宽 49，阈值约 2.45：差 1 视为持平，差 20 视为上升
        assertEquals("flat", MetricTrendAnalyzer.analyze(List.of(
                m("收缩压", 120, 0), m("收缩压", 121, 1))).get(0).direction());
        assertEquals("rising", MetricTrendAnalyzer.analyze(List.of(
                m("收缩压", 120, 0), m("收缩压", 140, 1))).get(0).direction());
        assertEquals("falling", MetricTrendAnalyzer.analyze(List.of(
                m("收缩压", 140, 0), m("收缩压", 118, 1))).get(0).direction());
    }

    @Test
    void singleSampleReportsInsufficientData() {
        MetricTrendAnalyzer.Trend t = MetricTrendAnalyzer.analyze(List.of(m("体重", 70, 0))).get(0);
        assertEquals(1, t.samples());
        assertEquals("unknown", t.direction());
        assertTrue(t.note().contains("仅 1 次"), "实际: " + t.note());
    }

    @Test
    void multipleMetricTypesAreAnalyzedIndependently() {
        List<MetricTrendAnalyzer.Trend> trends = MetricTrendAnalyzer.analyze(List.of(
                m("收缩压", 150, 0), m("空腹血糖", 5.0, 0),
                m("收缩压", 152, 1), m("空腹血糖", 5.1, 1)));
        assertEquals(2, trends.size());
        MetricTrendAnalyzer.Trend bp = trends.stream()
                .filter(t -> t.metricType().equals("收缩压")).findFirst().orElseThrow();
        MetricTrendAnalyzer.Trend sugar = trends.stream()
                .filter(t -> t.metricType().equals("空腹血糖")).findFirst().orElseThrow();
        assertEquals(2, bp.consecutiveAbnormal());
        assertEquals(0, sugar.consecutiveAbnormal());
        assertEquals("mmHg", bp.unit());
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(MetricTrendAnalyzer.analyze(List.of()).isEmpty());
        assertTrue(MetricTrendAnalyzer.analyze(null).isEmpty());
    }
}
