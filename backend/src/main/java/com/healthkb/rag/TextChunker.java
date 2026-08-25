package com.healthkb.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库切分，由 LangChain4j 的 {@link DocumentSplitter}
 * （{@code DocumentSplitters.recursive}，见 {@code RagConfig}）实现，
 * 按段落 → 句子 → 词的层级递归切分，比原先的定长滑窗更贴合医学长文。
 */
@Component
public class TextChunker {

    private final DocumentSplitter splitter;

    public TextChunker(DocumentSplitter splitter) {
        this.splitter = splitter;
    }

    public List<String> chunk(String text) {
        if (text == null) {
            return List.of();
        }
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (TextSegment segment : splitter.split(Document.from(cleaned))) {
            String piece = segment.text() == null ? "" : segment.text().trim();
            if (!piece.isEmpty()) {
                parts.add(piece);
            }
        }
        return parts.isEmpty() ? List.of(cleaned) : parts;
    }
}
