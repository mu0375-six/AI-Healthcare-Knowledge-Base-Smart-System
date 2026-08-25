package com.healthkb.common;

import java.util.List;
import java.util.Locale;

/**
 * 急症识别的唯一出处。
 *
 * <p>原先这套判断只长在 {@code TriageService} 里，而问答（{@code ChatService}）
 * 完全没有引用 —— 结果是：用户在问答里说「胸痛、出冷汗」时，是否提示立即就医
 * 完全取决于大模型当次怎么发挥；反倒是没配 API Key、退回模板回答时，
 * 那句「请立即急诊」一定会出现。优先级正好反了。
 *
 * <p>医疗场景里急症升级不能交给模型的随机性，所以抽到这里，
 * 由分诊与问答共用同一份确定性规则。
 */
public final class EmergencyRules {

    private EmergencyRules() {
    }

    /** 单独出现即视为急症警示的症状。 */
    private static final List<String> RED_FLAGS = List.of(
            "昏迷", "意识不清", "不省人事", "呼之不应", "大出血", "喷血",
            "严重过敏", "喉头水肿", "过敏性休克", "抽搐不止", "中毒", "自杀");

    /** 需与伴随症状同时出现才升级的主症状。 */
    private static final List<String> CHEST_PAIN = List.of("胸痛", "胸口痛", "压榨");

    /** 与主症状组合后提示危急的伴随症状。 */
    private static final List<String> COMPANIONS = List.of("呼吸困难", "喘不过气", "大汗", "濒死");

    /**
     * 返回命中的症状描述；未命中返回 {@code null}。
     * 返回具体命中词而不是布尔值，是为了让提示能说清「你提到的哪个症状」。
     */
    public static String match(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.toLowerCase(Locale.ROOT);
        for (String flag : RED_FLAGS) {
            if (t.contains(flag)) {
                return flag;
            }
        }
        for (String main : CHEST_PAIN) {
            if (!t.contains(main)) {
                continue;
            }
            for (String companion : COMPANIONS) {
                if (t.contains(companion)) {
                    return main + "伴" + companion;
                }
            }
        }
        return null;
    }

    public static boolean isEmergency(String text) {
        return match(text) != null;
    }

    /**
     * 命中时返回一段固定的告警，未命中返回空串。
     * 走 Markdown 引用块，前端已有渲染，不需要额外改动。
     */
    public static String banner(String text) {
        String hit = match(text);
        if (hit == null) {
            return "";
        }
        return "> ⚠️ **急症提示**：你描述的「" + hit + "」属于危急信号。\n"
                + "> 请立即拨打 **120** 或前往最近医院急诊科，**不要等待下面的回答**。\n\n";
    }
}
