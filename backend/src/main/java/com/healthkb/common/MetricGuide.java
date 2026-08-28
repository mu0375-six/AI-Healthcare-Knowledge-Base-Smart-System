package com.healthkb.common;

import java.util.LinkedHashMap;
import java.util.Locale;
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

    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        // 英文/缩略指标名 → 中文标准名。真实化验单（尤其境外报告）常见英文打印；
        // 报告解析与档案导入统一先过一层 normalize，否则「写入档案」认不出英文报告。
        ALIASES.put("glucose", "空腹血糖");
        ALIASES.put("fasting glucose", "空腹血糖");
        ALIASES.put("fbg", "空腹血糖");
        ALIASES.put("blood sugar", "空腹血糖");
        ALIASES.put("postprandial glucose", "餐后血糖");
        ALIASES.put("2hpg", "餐后血糖");
        ALIASES.put("hba1c", "糖化血红蛋白");
        ALIASES.put("a1c", "糖化血红蛋白");
        ALIASES.put("glycated hemoglobin", "糖化血红蛋白");
        ALIASES.put("glycohemoglobin", "糖化血红蛋白");
        ALIASES.put("systolic", "收缩压");
        ALIASES.put("systolic bp", "收缩压");
        ALIASES.put("diastolic", "舒张压");
        ALIASES.put("diastolic bp", "舒张压");
        ALIASES.put("weight", "体重");
        ALIASES.put("body weight", "体重");
        ALIASES.put("cholesterol", "总胆固醇");
        ALIASES.put("total cholesterol", "总胆固醇");
        ALIASES.put("triglycerides", "甘油三酯");
        ALIASES.put("triglyceride", "甘油三酯");
        ALIASES.put("urea nitrogen", "尿素氮");
        ALIASES.put("bun", "尿素氮");
        ALIASES.put("urea nitrogen bun", "尿素氮");
        ALIASES.put("creatinine", "肌酐");
        ALIASES.put("creatinine serum", "肌酐");
        ALIASES.put("creatinine, serum", "肌酐");
        ALIASES.put("alt", "谷丙转氨酶");
        ALIASES.put("sgpt", "谷丙转氨酶");
        ALIASES.put("alt sgpt", "谷丙转氨酶");
        ALIASES.put("ast", "谷草转氨酶");
        ALIASES.put("sgot", "谷草转氨酶");
        ALIASES.put("ast sgot", "谷草转氨酶");
        ALIASES.put("alkaline phosphatase", "碱性磷酸酶");
        ALIASES.put("alkaline phos", "碱性磷酸酶");
        ALIASES.put("phos", "碱性磷酸酶");
        ALIASES.put("bilirubin total", "总胆红素");
        ALIASES.put("calcium", "钙");
        ALIASES.put("potassium", "钾");
        ALIASES.put("sodium", "钠");
        ALIASES.put("chloride", "氯");
        ALIASES.put("albumin", "白蛋白");
        ALIASES.put("protein", "总蛋白");
        ALIASES.put("total protein", "总蛋白");
        ALIASES.put("bilirubin", "总胆红素");
        ALIASES.put("alkaline phosphatase", "碱性磷酸酶");
        ALIASES.put("creatine kinase", "肌酸激酶");
        ALIASES.put("white blood cell", "白细胞");
        ALIASES.put("wbc", "白细胞");
        ALIASES.put("hemoglobin", "血红蛋白");
        ALIASES.put("hgb", "血红蛋白");
        ALIASES.put("heart rate", "心率");
        ALIASES.put("pulse", "心率");
        ALIASES.put("uric acid", "尿酸");
    }

    /**
     * 报告指标名归一：去括号注解（[BUN]、[SGPT]）→ 小写查别名 → 中文标准名；
     * 已是中文或未收录的指标按原样返回（去掉首尾空白）。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        // 逗号/括号注解统一视为分隔（"CREATININE, SERUM" / "UREA NITROGEN [BUN]"）
        String cleaned = raw.replaceAll("[\\[\\]（）()【】,]", " ").replaceAll("\\s+", " ").trim();
        // 行首常带检验项编号（如 "1023 GLUCOSE"），剥掉再做别名匹配
        cleaned = cleaned.replaceFirst("^\\d{1,6}\\s+", "");
        String alias = ALIASES.get(cleaned.toLowerCase(Locale.ROOT));
        return alias == null ? cleaned : alias;
    }

    private MetricGuide() {
    }

    /** 全部参考区间，供 /api/health/reference 下发 —— 阈值只在此维护一份。 */
    public static Map<String, Band> bands() {
        return java.util.Collections.unmodifiableMap(BANDS);
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
