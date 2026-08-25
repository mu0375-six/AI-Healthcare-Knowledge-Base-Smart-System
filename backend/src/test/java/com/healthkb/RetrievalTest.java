package com.healthkb;

import com.healthkb.rag.Citation;
import com.healthkb.rag.RagService;
import com.healthkb.rag.ScoredChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RetrievalTest {

    @Autowired
    RagService ragService;

    @Test
    void blankQueryReturnsEmptyInsteadOfThrowing() {
        // LangChain4j 的 Query 不接受空串，RagService 需自行短路
        assertTrue(ragService.retrieve("", 5).isEmpty());
        assertTrue(ragService.retrieve("   ", 5).isEmpty());
        assertTrue(ragService.retrieve(null, 5).isEmpty());
    }

    @Test
    void metforminQueryHitsSeededDrugChunk() {
        List<ScoredChunk> hits = ragService.retrieve("二甲双胍", 5);
        assertFalse(hits.isEmpty());
        boolean match = hits.stream().anyMatch(c ->
                (c.getTitle() != null && c.getTitle().contains("二甲双胍"))
                        || (c.getContent() != null && c.getContent().contains("二甲双胍")));
        assertTrue(match, "应检索到二甲双胍相关知识块，实际: "
                + hits.stream().map(ScoredChunk::getTitle).toList());
    }

    @Test
    void soreThroatDoesNotCiteHypertensionOrDiet() {
        List<ScoredChunk> hits = ragService.retrieve("我最近喉咙有点痛，并且背部酸痛", 5);
        boolean offTopic = hits.stream().anyMatch(c -> {
            String t = c.getTitle() == null ? "" : c.getTitle();
            return t.contains("高血压") || t.contains("糖尿病") || t.contains("健康饮食")
                    || t.contains("钠") || t.contains("心脑血管");
        });
        assertFalse(offTopic, "日常咽痛不应挂上无关指南，实际: "
                + hits.stream().map(ScoredChunk::getTitle).toList());
    }

    @Test
    void citationCardRequiresQuestionTerm() {
        String q = "我最近喉咙有点痛，并且背部酸痛";
        Citation flu = new Citation("季节性流感", "WHO", "每年约有10亿例季节性流感病例", "疾病指南");
        Citation cvd = new Citation("心脑血管疾病防治行动实施方案（2023—2030年）", "NHC", "降低心脑血管疾病过早死亡", "政策规范");
        Citation diet = new Citation("健康饮食", "WHO", "减少饱和脂肪和游离糖", "疾病指南");
        assertTrue(ragService.visibleCitations(q, List.of(flu, cvd, diet)).isEmpty());
    }
}
