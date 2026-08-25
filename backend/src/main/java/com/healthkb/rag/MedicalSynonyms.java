package com.healthkb.rag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 医学别名表，供检索的词法环节做同义展开。
 *
 * <p>动机：用户口语和文献用词经常一个字都不重合 —— 「心梗」对「心肌梗死」、
 * 「拉肚子」对「腹泻」、「血糖高」对「糖尿病」。{@link RagService} 的词法环节
 * 是按字面子串判断的，不展开别名的话，向量召回回来的正确文档会在词法环节
 * 被判成不相关，配了真实 embedding 也拿不到收益。
 *
 * <p>收词标准：只收<b>同一所指</b>的写法。像「头痛 / 偏头痛」「肥胖 / 超重」
 * 这种相关但不等价的词一律不收 —— 一旦收进来，问咽痛就可能挂上高血压指南，
 * 正是 {@code RetrievalTest} 在防的那类串台。
 */
public final class MedicalSynonyms {

    private MedicalSynonyms() {
    }

    /** 每组内部两两互为别名，展开时不分方向。 */
    private static final List<Set<String>> GROUPS = List.of(
            Set.of("心梗", "心肌梗死", "心肌梗塞"),
            Set.of("脑梗", "脑梗死", "脑梗塞", "缺血性脑卒中"),
            Set.of("中风", "卒中", "脑卒中"),
            Set.of("冠心病", "冠状动脉粥样硬化性心脏病"),
            Set.of("房颤", "心房颤动"),
            Set.of("高血压", "血压高", "高血压病"),
            Set.of("低血压", "血压低"),
            Set.of("降压药", "抗高血压药", "降血压药"),
            Set.of("糖尿病", "血糖高", "高血糖"),
            Set.of("降糖药", "降血糖药"),
            Set.of("二甲双胍", "metformin"),
            Set.of("高血脂", "高脂血症", "血脂异常", "血脂高"),
            Set.of("慢阻肺", "慢性阻塞性肺疾病", "copd"),
            Set.of("哮喘", "支气管哮喘"),
            Set.of("上感", "上呼吸道感染"),
            Set.of("发烧", "发热"),
            Set.of("咽痛", "喉咙痛", "嗓子疼", "咽喉痛"),
            Set.of("拉肚子", "腹泻"),
            Set.of("便秘", "排便困难"),
            Set.of("甲亢", "甲状腺功能亢进"),
            Set.of("甲减", "甲状腺功能减退"),
            Set.of("减钠", "减盐", "限盐", "钠摄入"),
            Set.of("身体活动", "体育活动", "体力活动"),
            Set.of("肥胖", "肥胖症"),
            Set.of("骨质疏松", "骨量减少症"),
            Set.of("胃食管反流", "反流性食管炎"),
            Set.of("肾结石", "尿路结石"),
            Set.of("痛风", "高尿酸血症")
    );

    private static final Map<String, Set<String>> INDEX = buildIndex();

    private static Map<String, Set<String>> buildIndex() {
        Map<String, Set<String>> index = new HashMap<>();
        for (Set<String> group : GROUPS) {
            for (String word : group) {
                Set<String> others = new LinkedHashSet<>(group);
                others.remove(word);
                index.put(word, Set.copyOf(others));
            }
        }
        return Map.copyOf(index);
    }

    /** 返回 term 的别名（不含自身）；无别名时返回空集合。 */
    public static Set<String> aliasesOf(String term) {
        if (term == null || term.isBlank()) {
            return Set.of();
        }
        return INDEX.getOrDefault(term.toLowerCase(java.util.Locale.ROOT), Set.of());
    }

    /**
     * 把 terms 里每个词的别名并入结果。
     * 只展开一层：别名的别名同属一组，已经在同一次展开里了。
     */
    public static List<String> expand(Collection<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>(terms);
        for (String term : terms) {
            out.addAll(aliasesOf(term));
        }
        return new ArrayList<>(out);
    }

    /** 词表规模，供自检与测试用。 */
    public static int size() {
        return INDEX.size();
    }
}
