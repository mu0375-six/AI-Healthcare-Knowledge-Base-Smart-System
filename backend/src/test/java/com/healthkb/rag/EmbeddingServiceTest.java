package com.healthkb.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 哈希兜底向量的行为契约：这是未配置 embedding 服务时的唯一检索依据，
 * 确定性与区分度坏了，离线演示的检索就整体失效。
 */
class EmbeddingServiceTest {

    private final EmbeddingService service = new EmbeddingService(256);

    @Test
    void sameTextYieldsIdenticalVector() {
        assertArrayEquals(service.embed("高血压如何控制"), service.embed("高血压如何控制"));
    }

    @Test
    void respectsConfiguredDimension() {
        assertEquals(256, service.embed("任意文本").length);
        assertEquals(64, new EmbeddingService(64).embed("任意文本").length);
    }

    @Test
    void differentTextsAreSeparable() {
        float[] a = service.embed("高血压如何控制");
        float[] b = service.embed("儿童疫苗接种时间表");
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        // 均已 L2 归一化，余弦=点积；完全一致才是 1，不同文本应明显低于 1
        assertTrue(dot < 0.9, "不同文本的余弦相似度不应接近 1，实际 " + dot);
    }

    @Test
    void blankTextGivesZeroVector() {
        float[] v = service.embed("  ");
        for (float x : v) {
            assertEquals(0f, x);
        }
    }

    @Test
    void outputIsL2Normalized() {
        float[] v = service.embed("糖尿病饮食注意事项");
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-4);
    }

    @Test
    void normalizeStripsPunctuationAndLowercases() {
        assertEquals("abc123", EmbeddingService.normalize("A B, C! 123"));
        assertEquals("中文内容", EmbeddingService.normalize("中，文！内 容"));
        assertFalse(EmbeddingService.normalize("X-Y").contains("-"));
    }
}
