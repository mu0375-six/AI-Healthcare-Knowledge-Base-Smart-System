package com.healthkb.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MetricGuide {

    public record Band(double low, double high, String unit) {
    }

    private static final Map<String, Band> BANDS = new LinkedHashMap<>();

    static {
        BANDS.put("收缩压", new Band(90, 139, "mmHg"));
        BANDS.put("舒张压", new Band(60, 89, "mmHg"));
        BANDS.put("空腹血糖", new Band(3.9, 6.1, "mmol/L"));
        BANDS.put("餐后血糖", new Band(0, 7.8, "mmol/L"));
        BANDS.put("糖化血红蛋白", new Band(4.0, 6.0, "%"));
        BANDS.put("体重", new Band(40, 120, "kg"));
        BANDS.put("总胆固醇", new Band(0, 5.2, "mmol/L"));
        BANDS.put("甘油三酯", new Band(0, 1.7, "mmol/L"));
    }

    private MetricGuide() {
    }

    public static Band band(String type) {
        return type == null ? null : BANDS.get(type.trim());
    }

    public static String unitOf(String type) {
        Band b = band(type);
        return b == null ? "" : b.unit();
    }

    public static String flag(String type, Double value) {
        Band b = band(type);
        if (b == null || value == null) {
            return "unknown";
        }
        if (value < b.low() - 1e-6) {
            return "low";
        }
        if (value > b.high() + 1e-6) {
            return "high";
        }
        return "normal";
    }

    public static String flagText(String flag) {
        return switch (flag) {
            case "high" -> "偏高";
            case "low" -> "偏低";
            case "normal" -> "正常";
            default -> "待看";
        };
    }

    public static Double bmi(Double heightCm, Double weightKg) {
        if (heightCm == null || weightKg == null || heightCm <= 0) {
            return null;
        }
        return weightKg / Math.pow(heightCm / 100.0, 2);
    }

    public static String bmiLabel(Double bmi) {
        if (bmi == null) {
            return "";
        }
        if (bmi < 18.5) {
            return "偏瘦";
        }
        if (bmi < 24) {
            return "正常";
        }
        if (bmi < 28) {
            return "超重";
        }
        return "肥胖";
    }
}
