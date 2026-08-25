package com.healthkb.service;

import com.healthkb.common.EmergencyRules;
import com.healthkb.common.MedicalConstants;
import com.healthkb.dto.TriageDtos;
import com.healthkb.rag.RagService;
import com.healthkb.rag.ScoredChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TriageService {

    private final RagService ragService;

    private static final List<Rule> RULES = List.of(
            new Rule("急诊科", List.of("昏迷", "意识不清", "大出血", "咯血", "吐血", "呕血",
                    "严重过敏", "喉头水肿", "抽搐", "中毒", "自杀", "休克", "猝死"), 8, "emergency",
                    "出现危及生命的症状，应立即急诊"),
            new Rule("心血管内科", List.of("胸痛", "胸闷", "心悸", "心慌", "血压", "高血压", "心绞痛", "气促"), 3, "outpatient",
                    "症状提示心血管系统问题"),
            new Rule("内分泌科", List.of("血糖", "糖尿病", "口渴", "多饮", "多尿", "甲状腺", "消瘦"), 3, "outpatient",
                    "症状提示内分泌代谢问题"),
            new Rule("呼吸内科", List.of("咳嗽", "咳痰", "气喘", "喘息", "呼吸困难", "发烧", "发热", "咽痛"), 3, "outpatient",
                    "症状提示呼吸系统问题"),
            new Rule("消化内科", List.of("腹痛", "反酸", "烧心", "胃痛", "腹泻", "便秘", "黑便", "恶心", "呕吐"), 3, "outpatient",
                    "症状提示消化系统问题"),
            new Rule("神经内科", List.of("头痛", "头晕", "眩晕", "麻木", "偏瘫", "口角歪斜", "抽搐", "记忆力"), 3, "outpatient",
                    "症状提示神经系统问题"),
            new Rule("皮肤科", List.of("皮疹", "瘙痒", "红肿", "过敏", "痤疮", "疱疹"), 3, "outpatient",
                    "症状提示皮肤问题"),
            new Rule("儿科", List.of("婴幼儿", "小孩", "儿童", "宝宝"), 4, "outpatient",
                    "儿童患者建议儿科评估"),
            new Rule("妇产科", List.of("月经", "停经", "怀孕", "孕期", "阴道出血", "下腹痛"), 3, "outpatient",
                    "症状提示妇科或产科问题"),
            new Rule("全科", List.of("体检", "乏力", "不舒服", "咨询"), 1, "self_care",
                    "症状尚不典型，可先全科评估")
    );

    public TriageDtos.Response triage(TriageDtos.Request req) {
        String text = req.getSymptoms() == null ? "" : req.getSymptoms().trim();
        Map<String, ScoreAcc> scores = new LinkedHashMap<>();
        boolean emergency = isEmergency(text);

        for (Rule rule : RULES) {
            int hits = 0;
            List<String> matched = new ArrayList<>();
            for (String kw : rule.keywords) {
                if (text.contains(kw)) {
                    hits++;
                    matched.add(kw);
                }
            }
            if (hits > 0) {
                ScoreAcc acc = scores.computeIfAbsent(rule.dept, k -> new ScoreAcc(rule.dept));
                acc.score += hits * rule.weight;
                acc.reason = rule.reason + "（匹配：" + String.join("、", matched) + "）";
                acc.urgency = rule.urgency;
            }
        }

        List<ScoredChunk> retrieved = ragService.retrieve(text + " 科室导诊", 6);
        for (ScoredChunk c : retrieved) {
            String dept = mapDeptFromTitle(c.getTitle());
            if (dept == null) {
                continue;
            }
            ScoreAcc acc = scores.computeIfAbsent(dept, ScoreAcc::new);
            acc.score += c.getScore() * 6;
            if (acc.reason == null) {
                acc.reason = "知识库《" + c.getTitle() + "》与描述相近";
                acc.urgency = "outpatient";
            }
        }

        if (emergency) {
            ScoreAcc em = scores.computeIfAbsent("急诊科", ScoreAcc::new);
            em.score += 50;
            em.urgency = "emergency";
            em.reason = "命中急诊警示症状，应立即就医或拨打急救电话";
        }

        if (req.getAge() != null && req.getAge() < 14) {
            ScoreAcc p = scores.computeIfAbsent("儿科", ScoreAcc::new);
            p.score += 4;
            if (p.reason == null) {
                p.reason = "患儿年龄小于 14 岁，建议儿科";
                p.urgency = "outpatient";
            }
        }
        if (req.getAge() != null && req.getAge() >= 65 && scores.containsKey("心血管内科")) {
            scores.get("心血管内科").score += 1.5;
        }

        if (scores.isEmpty()) {
            scores.put("全科", new ScoreAcc("全科", 1, "症状描述较笼统，建议先到全科评估", "self_care"));
        }

        List<TriageDtos.DepartmentHit> ranked = scores.values().stream()
                .sorted(Comparator.comparingDouble((ScoreAcc a) -> a.score).reversed())
                .limit(3)
                .map(a -> new TriageDtos.DepartmentHit(a.dept, round(a.score), a.reason, a.urgency))
                .toList();

        String topUrgency = emergency ? "emergency" : ranked.get(0).getUrgency();
        if (!emergency && containsAny(text, "发烧", "发热", "咳嗽", "流涕") && text.length() < 20) {
            if ("outpatient".equals(topUrgency)) {
                topUrgency = "self_care";
                ranked.get(0).setUrgency("self_care");
            }
        }

        TriageDtos.Response resp = new TriageDtos.Response();
        resp.setUrgency(topUrgency);
        resp.setDepartments(ranked);
        resp.setSummary(buildSummary(text, ranked, topUrgency));
        resp.setDisclaimer(MedicalConstants.DISCLAIMER);
        return resp;
    }

    /** 规则本体已抽到 {@link EmergencyRules}，与问答共用同一份判断。 */
    public boolean isEmergency(String text) {
        return EmergencyRules.isEmergency(text);
    }

    private String buildSummary(String text, List<TriageDtos.DepartmentHit> ranked, String urgency) {
        StringBuilder sb = new StringBuilder();
        if ("emergency".equals(urgency)) {
            sb.append("根据您描述的症状，系统判断存在急诊警示信号，请立即前往急诊科或拨打急救电话，不要等待网答。");
        } else {
            sb.append("根据症状「").append(brief(text, 30)).append("」，优先建议就诊：")
                    .append(ranked.get(0).getDepartment()).append("。");
        }
        sb.append("\n\n").append(MedicalConstants.DISCLAIMER);
        return sb.toString();
    }

    private static String mapDeptFromTitle(String title) {
        if (title == null) {
            return null;
        }
        for (String d : List.of("急诊科", "心血管内科", "内分泌科", "呼吸内科", "消化内科",
                "神经内科", "皮肤科", "儿科", "妇产科", "全科")) {
            if (title.contains(d.replace("内科", "")) || title.contains(d)) {
                return d;
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String... kws) {
        for (String k : kws) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String brief(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record Rule(String dept, List<String> keywords, double weight, String urgency, String reason) {
    }

    private static class ScoreAcc {
        final String dept;
        double score;
        String reason;
        String urgency;

        ScoreAcc(String dept) {
            this.dept = dept;
        }

        ScoreAcc(String dept, double score, String reason, String urgency) {
            this.dept = dept;
            this.score = score;
            this.reason = reason;
            this.urgency = urgency;
        }
    }
}
