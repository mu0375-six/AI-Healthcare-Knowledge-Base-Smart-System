package com.healthkb.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重排打分与查询词展开的单元测试。
 * 放在 com.healthkb.rag 包下是为了直接调包内可见的静态方法，
 * 不必为了测两个纯函数去起 Spring 上下文。
 */
class RerankScoringTest {

    private static ScoredChunk chunk(String title, String content, float score) {
        return new ScoredChunk(1L, 1L, content, title, "疾病指南", "WHO", score);
    }

    @Test
    void queryTermsExpandSpokenFormToClinicalTerm() {
        List<String> terms = RagService.queryTerms("心梗后要注意什么");
        assertTrue(terms.contains("心梗"));
        assertTrue(terms.contains("心肌梗死"), "口语词应展开出文献用词，实际: " + terms);
    }

    @Test
    void lexicalScoreRewardsLongerAndMoreSpecificMatch() {
        List<String> terms = RagService.queryTerms("心梗后要注意什么");
        // 正文只写「心肌梗死」，一个字都不与「心梗」重合 —— 展开别名后应当能对上
        double specific = RagService.lexicalScore(terms, chunk("心肌梗死的二级预防", "急性心肌梗死后应长期服药", 0.8f));
        double none = RagService.lexicalScore(terms, chunk("健康饮食", "减少游离糖摄入", 0.8f));
        assertTrue(specific > 0, "别名展开后应有词法命中，实际得分 " + specific);
        assertEquals(0d, none);
        assertTrue(specific > RagService.lexicalScore(terms, chunk("心肌图谱", "心肌细胞结构", 0.8f)),
                "命中四字全称应比只命中「心肌」得分高");
    }

    @Test
    void combinedScoreLetsVectorScoreCarryASynonymousHit() {
        List<String> terms = RagService.queryTerms("心梗后要注意什么");
        ScoredChunk relevant = chunk("心肌梗死的二级预防", "急性心肌梗死后应长期服药", 0.82f);
        ScoredChunk offTopic = chunk("健康饮食", "减少游离糖摄入", 0.55f);
        double hi = RagService.combinedScore(relevant, terms, 0.3);
        double lo = RagService.combinedScore(offTopic, terms, 0.3);
        assertTrue(hi > lo, "相关块的综合分应更高: " + hi + " vs " + lo);
        // 默认阈值 0.55：相关的过、不相关的不过
        assertTrue(hi >= 0.55, "相关块不应被阈值挡掉，实际 " + hi);
        assertTrue(lo < 0.55, "无关块应被阈值挡掉，实际 " + lo);
    }

    @Test
    void lexicalWeightIsClampedAndZeroWeightFallsBackToVectorScore() {
        List<String> terms = RagService.queryTerms("高血压");
        ScoredChunk c = chunk("高血压", "限盐", 0.7f);
        assertEquals(0.7d, RagService.combinedScore(c, terms, 0.0), 1e-6);
        assertEquals(RagService.combinedScore(c, terms, 1.0),
                RagService.combinedScore(c, terms, 5.0), 1e-6, "权重应被夹到 [0,1]");
    }
}
