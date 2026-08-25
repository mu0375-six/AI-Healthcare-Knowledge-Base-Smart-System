package com.healthkb.service;

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
            "([\\u4e00-\\u9fa5A-Za-z0-9（）()\\-]{2,20})\\s*[:：]?\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*"
                    + "([a-zA-Zμ%/升LmmHg]+(?:/[a-zA-Z]+)?)?\\s*"
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
        Matcher m = LINE.matcher(text);
        while (m.find()) {
            String name = normalizeName(m.group(1));
            if (skipName(name)) {
                continue;
            }
            String value = m.group(2);
            String unit = m.group(3);
            String low = m.group(4);
            String high = m.group(5);
            Ref def = lookupRef(name);
            if (unit == null && def != null) {
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
            item.setFlag(flag(parseD(value), lo, hi));
            items.add(item);
        }
        return items;
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

    private static String normalizeName(String raw) {
        return raw.replaceAll("[\\s:：]+$", "").trim();
    }

    private static boolean skipName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.length() < 2 || n.matches("\\d+") || n.contains("参考") || n.contains("范围");
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

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private record Ref(double low, double high, String unit) {
    }
}
