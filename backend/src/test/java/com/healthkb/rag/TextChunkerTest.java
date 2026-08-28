package com.healthkb.rag;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切分器此前零测试。配置与 RagConfig 保持一致（600/80），
 * 保证测到的就是线上真实行为。
 */
class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(DocumentSplitters.recursive(600, 80));

    @Test
    void nullAndBlankYieldNoChunks() {
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("   \n  ").isEmpty());
    }

    @Test
    void shortTextStaysWhole() {
        List<String> parts = chunker.chunk("高血压是最常见的慢性病。");
        assertEquals(1, parts.size());
        assertEquals("高血压是最常见的慢性病。", parts.get(0));
    }

    @Test
    void crlfIsNormalizedBeforeSplitting() {
        List<String> parts = chunker.chunk("第一段内容。\r\n第二段内容。");
        assertEquals(1, parts.size());
        assertTrue(parts.get(0).contains("\n"));
        assertTrue(!parts.get(0).contains("\r"));
    }

    @Test
    void longTextIsSplitIntoMultipleChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append("高血压患者应当减少钠盐摄入，每日食盐不超过五克，同时配合规律的有氧运动。段落").append(i).append("。\n\n");
        }
        List<String> parts = chunker.chunk(sb.toString());
        assertTrue(parts.size() >= 3, "2000+ 字文本应切成多块，实际 " + parts.size());
        for (String p : parts) {
            assertTrue(!p.isBlank());
        }
        // 切块不能凭空发明内容：所有块拼回去长度应与原文（归一换行后）相当
        int joined = String.join("", parts).length();
        assertTrue(joined <= sb.toString().replace("\r\n", "\n").length());
    }
}
