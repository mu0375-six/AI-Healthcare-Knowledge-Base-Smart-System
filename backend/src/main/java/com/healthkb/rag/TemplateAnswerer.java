package com.healthkb.rag;

import com.healthkb.common.MedicalConstants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public class TemplateAnswerer {

    public String answer(String question, List<ScoredChunk> chunks, List<ChatTurn> history, String extraContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("您好，我已结合健康知识库检索结果，就您的问题进行整理。\n\n");
        sb.append("**您的问题：** ").append(question == null ? "" : question.trim()).append("\n\n");

        if (history != null && !history.isEmpty()) {
            ChatTurn lastUser = null;
            for (int i = history.size() - 1; i >= 0; i--) {
                if ("user".equalsIgnoreCase(history.get(i).getRole())) {
                    lastUser = history.get(i);
                    break;
                }
            }
            if (lastUser != null && question != null && !question.contains(lastUser.getContent())) {
                sb.append("结合您前面提到的「").append(brief(lastUser.getContent(), 40)).append("」，");
            }
        }

        if (chunks == null || chunks.isEmpty()) {
            sb.append("当前知识库中未检索到足够匹配的条目，以下仅为一般健康科普提示，不能作为诊断依据。\n\n");
            sb.append("## 建议\n");
            sb.append("- 请尽量补充症状出现时间、伴随表现、基础疾病和正在服用的药物。\n");
            sb.append("- 若症状加重、持续不缓解或出现胸痛、呼吸困难、意识改变、大出血等，请立即急诊。\n");
            sb.append("- 用药、检查和治疗方案必须由执业医师面诊后确定。\n\n");
        } else {
            sb.append("## 核心要点\n");
            int limit = Math.min(3, chunks.size());
            for (int i = 0; i < limit; i++) {
                ScoredChunk c = chunks.get(i);
                sb.append("- **").append(nullToEmpty(c.getTitle())).append("**（")
                        .append(nullToEmpty(c.getCategory())).append("）：")
                        .append(brief(c.getContent(), 90)).append("\n");
            }
            sb.append("\n## 详细说明\n");
            for (int i = 0; i < chunks.size(); i++) {
                ScoredChunk c = chunks.get(i);
                sb.append("### ").append(i + 1).append(". ").append(nullToEmpty(c.getTitle())).append("\n");
                sb.append(paragraph(c.getContent(), 280)).append("\n\n");
            }
            sb.append("## 就医提示\n");
            sb.append("- 上述内容来自知识库摘录，不能替代个体化诊疗。\n");
            sb.append("- 处方药（如阿司匹林、二甲双胍、氨氯地平）必须遵医嘱使用，勿自行加减量。\n");
            sb.append("- 出现危急症状请优先急诊，不要等待网上回复。\n\n");
        }

        if (extraContext != null && !extraContext.isBlank()) {
            sb.append("## 补充说明\n").append(extraContext.trim()).append("\n\n");
        }

        if (chunks != null && !chunks.isEmpty()) {
            sb.append("## 参考来源\n");
            StringJoiner joiner = new StringJoiner("\n");
            int i = 1;
            for (ScoredChunk c : chunks) {
                joiner.add(i + ". [" + nullToEmpty(c.getCategory()) + "] " + nullToEmpty(c.getTitle())
                        + " — " + nullToEmpty(c.getSource()));
                i++;
            }
            sb.append(joiner).append("\n\n");
        }
        sb.append(MedicalConstants.DISCLAIMER);
        return sb.toString();
    }

    public void stream(String full, AnswerSink sink) {
        if (full == null || full.isEmpty()) {
            return;
        }
        int i = 0;
        while (i < full.length()) {
            int end = Math.min(full.length(), i + step(full, i));
            sink.delta(full.substring(i, end));
            i = end;
        }
    }

    private static int step(String s, int i) {
        char c = s.charAt(i);
        if (c == '\n') {
            return 1;
        }
        if (s.length() - i >= 8 && (c == '#' || c == '-' || c == '*')) {
            return Math.min(12, s.length() - i);
        }
        return Character.isIdeographic(c) ? 1 : Math.min(4, s.length() - i);
    }

    private static String brief(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String paragraph(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
