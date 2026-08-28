package com.healthkb.service;

import com.healthkb.common.MetricGuide;
import com.healthkb.entity.ExamReportItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReportParser {

    private static final Pattern LINE = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z0-9（）()\\- \\t\\[\\],]{2,30}?)\\s*[:：]?\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*"
                    + "((?i:mmol/l|umol/l|μmol/l|mg/dl|g/dl|u/l|g/l|mmhg|ng/ml|pg/ml|mg/l|ug/l|μg/l|10\\^9/l|10\\^6/l|%))?\\s*"
                    + "(?:[（(]\\s*([0-9.]+)\\s*[-~～—至到]\\s*([0-9.]+)\\s*[)）])?");

    private static final Map<String, Ref> DEFAULT_REF = new LinkedHashMap<>();

    static {
        DEFAULT_REF.put("空腹血糖", new Ref(3.9, 6.1, "mmol/L"));
        DEFAULT_REF.put("餐后血糖", new Ref(0, 7.8, "mmol/L"));
        DEFAULT_REF.put("糖化血红蛋白", new Ref(4.0, 6.0, "%"));
        DEFAULT_REF.put("收缩压", new Ref(90, 139, "mmHg"));
        DEFAULT_REF.put("舒张压", new Ref(60, 89, "mmHg"));
        DEFAULT_REF.put("总胆固醇", new Ref(0, 5.2, "mmol/L"));
        DEFAULT_REF.put("甘油三酯", new Ref(0, 1.7, "mmol/L"));
        DEFAULT_REF.put("尿酸", new Ref(150, 420, "μmol/L"));
        DEFAULT_REF.put("尿素氮", new Ref(3.1, 8.0, "mmol/L"));
        DEFAULT_REF.put("肌酐", new Ref(57, 104, "μmol/L"));
        DEFAULT_REF.put("谷丙转氨酶", new Ref(0, 40, "U/L"));
        DEFAULT_REF.put("谷草转氨酶", new Ref(0, 40, "U/L"));
        DEFAULT_REF.put("碱性磷酸酶", new Ref(45, 125, "U/L"));
        DEFAULT_REF.put("肌酸激酶", new Ref(55, 170, "U/L"));
        DEFAULT_REF.put("钙", new Ref(2.11, 2.52, "mmol/L"));
        DEFAULT_REF.put("钾", new Ref(3.5, 5.3, "mmol/L"));
        DEFAULT_REF.put("钠", new Ref(137, 147, "mmol/L"));
        DEFAULT_REF.put("氯", new Ref(99, 110, "mmol/L"));
        DEFAULT_REF.put("白蛋白", new Ref(40, 55, "g/L"));
        DEFAULT_REF.put("总蛋白", new Ref(65, 85, "g/L"));
        DEFAULT_REF.put("总胆红素", new Ref(5, 21, "μmol/L"));
        DEFAULT_REF.put("白细胞", new Ref(3.5, 9.5, "10^9/L"));
        DEFAULT_REF.put("血红蛋白", new Ref(110, 160, "g/L"));
        DEFAULT_REF.put("心率", new Ref(60, 100, "次/分"));
        DEFAULT_REF.put("体重", new Ref(40, 80, "kg"));
        DEFAULT_REF.put("alt", new Ref(0, 40, "U/L"));
        DEFAULT_REF.put("ast", new Ref(0, 40, "U/L"));
    }

    public List<ExamReportItem> parse(String text) {
        List<ExamReportItem> items = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return items;
        }
        // 境外报告常带行首检验编号（"1023 GLUCOSE ..."）。先整行剥掉编号，
        // 否则编号数字会把「名称+数值」的正则拆出碎片匹配（如 "10" + "22"）。
        String cleaned = text.replaceAll("(?m)^\\s*\\d{1,6}\\s+(?=[A-Za-z\\u4e00-\\u9fa5（])", "");
        Matcher m = LINE.matcher(cleaned);
        while (m.find()) {
            String name = MetricGuide.normalize(m.group(1));
            if (skipName(name)) {
                continue;
            }
            Double v = parseD(m.group(2));
            double flagBase = v == null ? Double.NaN : v;
            String value = m.group(2);
            String unit = m.group(3); // 只可能是已知单位（正则白名单），不会吞进英文名称
            String low = m.group(4);
            String high = m.group(5);
            Ref def = lookupRef(name);
            if (unit == null && def != null) {
                // 境外报告常见 mg/dL：按公开换算系数归一到中文参考单位（mmol/L），
                // 否则「164 / 参考 3.9-6.1」会在同一行里出现两套单位体系
                Double converted = convertPubUnit(name, v);
                if (converted != null) {
                    value = fmt(converted);
                    flagBase = converted; // 高低判定也要基于换算后的值（偏低时尤其要紧）
                }
                unit = def.unit;
            }
            Double lo = parseD(low);
            Double hi = parseD(high);
            if (lo == null && def != null) {
                lo = def.low;
            }
            if (hi == null && def != null) {
                hi = def.high;
            }
            ExamReportItem item = new ExamReportItem();
            item.setName(name);
            item.setItemValue(value);
            item.setUnit(unit);
            if (lo != null && hi != null) {
                item.setRefRange(trimNum(lo) + "-" + trimNum(hi));
            }
            item.setFlag(flag(Double.isNaN(flagBase) ? null : flagBase, lo, hi));
            items.add(item);
        }
        return items;
    }

    /** mg/dL → mmol/L、g/dL → g/L 换算（仅针对中文参考单位体系内的指标；量级不对则不动）。 */
    private static Double convertPubUnit(String name, Double v) {
        if (v == null) {
            return null;
        }
        switch (name) {
            case "空腹血糖", "餐后血糖" -> {
                return v > 20 ? v / 18.0182 : null; // 20 mmol/L ≈ 360 mg/dL，以上必然是 mg/dL
            }
            case "总胆固醇" -> {
                return v > 12 ? v / 38.67 : null;   // 12 mmol/L ≈ 464 mg/dL
            }
            case "甘油三酯" -> {
                return v > 4 ? v / 88.57 : null;    // 4 mmol/L ≈ 354 mg/dL
            }
            case "白蛋白", "总蛋白", "血红蛋白" -> {
                return v < 30 ? v * 10 : null;      // g/dL 量级（白蛋白常见 3.5-5.5）
            }
            case "肌酐" -> {
                return v < 5 ? v * 88.4 : null;     // mg/dL（0.5-1.5）→ μmol/L
            }
            case "总胆红素" -> {
                return v < 3 ? v * 17.1 : null;     // mg/dL（0.2-1.3）→ μmol/L
            }
            case "尿素氮" -> {
                return v > 10 ? v / 2.8 : null;     // mg/dL（7-27）→ mmol/L
            }
            default -> {
                return null;
            }
        }
    }

    public String interpretItem(ExamReportItem item) {
        String flag = item.getFlag();
        String name = item.getName();
        if ("high".equals(flag)) {
            return name + "高于参考范围（" + item.getItemValue() + nvl(item.getUnit())
                    + "，参考 " + nvl(item.getRefRange()) + "）。建议结合症状复查，必要时就诊相关科室，勿自行用药。";
        }
        if ("low".equals(flag)) {
            return name + "低于参考范围（" + item.getItemValue() + nvl(item.getUnit())
                    + "，参考 " + nvl(item.getRefRange()) + "）。请由医师判断是否需要进一步检查。";
        }
        if ("normal".equals(flag)) {
            return name + "目前在常见参考范围内，仍需结合整体情况解读。";
        }
        return name + "未能匹配明确参考范围，请以检验单报告及临床医师意见为准。";
    }

    private static String flag(Double value, Double lo, Double hi) {
        if (value == null) {
            return "unknown";
        }
        if (lo != null && value < lo - 1e-6) {
            return "low";
        }
        if (hi != null && value > hi + 1e-6) {
            return "high";
        }
        if (lo != null || hi != null) {
            return "normal";
        }
        return "unknown";
    }

    private static boolean skipName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        // 中文指标名不以「号」结尾；以它收尾的是姓名/样本号/住院号等标识字段
        return n.length() < 2 || n.matches("\\d+") || n.contains("参考")
                || n.contains("范围") || n.endsWith("号");
    }

    private static Ref lookupRef(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Ref> e : DEFAULT_REF.entrySet()) {
            if (n.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Double parseD(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.valueOf(v);
    }

    /** 换算后的值：整数显示整数，其余保留 1 位小数（化验单常见精度）。 */
    private static String fmt(double v) {
        double r = Math.round(v * 10) / 10.0;
        return trimNum(r);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private record Ref(double low, double high, String unit) {
    }
}
