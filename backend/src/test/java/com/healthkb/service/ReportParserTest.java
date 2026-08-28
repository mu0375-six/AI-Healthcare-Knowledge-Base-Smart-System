package com.healthkb.service;

import com.healthkb.entity.ExamReportItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报告解析此前零测试，而这正是「检查报告解读」功能的正则核心。
 */
class ReportParserTest {

    private final ReportParser parser = new ReportParser();

    @Test
    void parsesHighGlucoseWithDefaultsFilled() {
        List<ExamReportItem> items = parser.parse("空腹血糖 7.2 mmol/L (3.9-6.1)\n");

        assertEquals(1, items.size());
        ExamReportItem it = items.get(0);
        assertEquals("空腹血糖", it.getName());
        assertEquals("7.2", it.getItemValue());
        assertEquals("mmol/L", it.getUnit());
        assertEquals("3.9-6.1", it.getRefRange());
        assertEquals("high", it.getFlag());
    }

    @Test
    void parsesBloodPressurePairOnSeparateLines() {
        String text = """
                收缩压 148 mmHg (90-139)
                舒张压 92 mmHg (60-89)
                """;

        List<ExamReportItem> items = parser.parse(text);

        assertEquals(2, items.size());
        assertEquals("收缩压", items.get(0).getName());
        assertEquals("high", items.get(0).getFlag());
        assertEquals("舒张压", items.get(1).getName());
        assertEquals("high", items.get(1).getFlag());
    }

    @Test
    void usesGivenRangeToFlagLowValueWithoutDefault() {
        // 尿酸在默认表里，但报文自带区间时优先用报文数值
        List<ExamReportItem> items = parser.parse("血尿酸 98 μmol/L (150-420)");

        assertEquals(1, items.size());
        assertEquals("low", items.get(0).getFlag());
        assertEquals("150-420", items.get(0).getRefRange());
    }

    @Test
    void skipsNoiseAndReferenceLines() {
        String text = """
                检验科报告单
                姓名：张某  样本号：123456789
                项目名称 结果 单位 参考范围
                空腹血糖 5.0 mmol/L (3.9-6.1)
                注：本结果仅对本样本负责
                """;

        List<ExamReportItem> items = parser.parse(text);

        assertEquals(1, items.size(), "只应解析出指标行");
        assertEquals("空腹血糖", items.get(0).getName());
    }

    @Test
    void blankInputYieldsEmptyList() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
    }

    @Test
    void parsesEnglishReportWithAliasesAndUnitConversion() {
        // 真实境外报告（mg/dL、无单位列、带行号与括号注解）——见 samples/real-lab-cmp.jpg
        String text = """
                1022 UREA NITROGEN [BUN] 27 HI
                1023 GLUCOSE 164 HI
                2046 ALT(SGPT) 47 HI
                """;

        List<ExamReportItem> items = parser.parse(text);
        assertEquals(3, items.size());

        ExamReportItem glucose = items.get(1);
        assertEquals("空腹血糖", glucose.getName(), "GLUCOSE 应对齐中文标准名");
        assertEquals("9.1", glucose.getItemValue(), "164 mg/dL 应换算为 9.1 mmol/L");
        assertEquals("mmol/L", glucose.getUnit());
        assertEquals("high", glucose.getFlag(), "换算后仍高于参考范围");

        assertEquals("尿素氮", items.get(0).getName(), "[BUN] 注解与括号应被剥离");
        assertEquals("谷丙转氨酶", items.get(2).getName(), "ALT(SGPT) 双别名应归一");
        assertEquals("high", items.get(2).getFlag(), "47 超过默认 0-40");
    }

    @Test
    void parsesCommaSeparatedEnglishNameAndAlbuminGPerDL() {
        List<ExamReportItem> items = parser.parse("CREATININE, SERUM 1.1\nALBUMIN 4.2\n");
        assertEquals(2, items.size());
        assertEquals("肌酐", items.get(0).getName(), "逗号分隔的名词应归一为中文标准名");
        assertEquals("白蛋白", items.get(1).getName());
        assertEquals("42", items.get(1).getItemValue(), "4.2 g/dL 应换算为 42 g/L");
        assertEquals("normal", items.get(1).getFlag(), "42 在 40-55 参考范围内");
    }

    @Test
    void convertsCreatinineAndBunFromMgPerDl() {
        // 真实化验单（samples/real-lab-cmp.jpg）：CREATININE 1.1 / BUN 27 / BILIRUBIN 0.7
        List<ExamReportItem> items = parser.parse("CREATININE, SERUM 1.1\nUREA NITROGEN [BUN] 27\nBILIRUBIN, TOTAL 0.7\n");
        assertEquals(3, items.size());
        assertEquals("97.2", items.get(0).getItemValue(), "1.1 mg/dL ≈ 97.2 μmol/L");
        assertEquals("normal", items.get(0).getFlag(), "97.2 在 57-97 参考范围内");
        assertEquals("9.6", items.get(1).getItemValue(), "27 mg/dL ≈ 9.6 mmol/L");
        assertEquals("high", items.get(1).getFlag(), "9.6 超过 3.1-8.0");
        assertEquals("12", items.get(2).getItemValue(), "0.7 mg/dL ≈ 12 μmol/L");
        assertEquals("normal", items.get(2).getFlag(), "12.0 在 5-21 参考范围内");
    }

    @Test
    void lowGlucoseInMgDlStillFlagsLowAfterConversion() {
        List<ExamReportItem> items = parser.parse("GLUCOSE 60\n");
        assertEquals(1, items.size());
        assertEquals("3.3", items.get(0).getItemValue(), "60 mg/dL ≈ 3.3 mmol/L");
        assertEquals("low", items.get(0).getFlag());
    }

    @Test
    void interpretCoversAllFourFlags() {
        assertTrue(parser.interpretItem(item("空腹血糖", "7.2", "mmol/L", "3.9-6.1", "high"))
                .contains("高于参考范围"));
        assertTrue(parser.interpretItem(item("血红蛋白", "95", "g/L", "110-160", "low"))
                .contains("低于参考范围"));
        assertTrue(parser.interpretItem(item("心率", "72", "次/分", "60-100", "normal"))
                .contains("常见参考范围内"));
        assertTrue(parser.interpretItem(item("某项", "1", "", "", "unknown"))
                .contains("未能匹配明确参考范围"));
    }

    private static ExamReportItem item(String name, String value, String unit, String ref, String flag) {
        ExamReportItem it = new ExamReportItem();
        it.setName(name);
        it.setItemValue(value);
        it.setUnit(unit);
        it.setRefRange(ref);
        it.setFlag(flag);
        return it;
    }
}
