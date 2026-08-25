package com.healthkb.rag.lc;

import com.healthkb.rag.VectorizeService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * 把项目自己的 {@link VectorizeService}（真实 embedding + 哈希兜底）
 * 适配成 LangChain4j 的 {@link EmbeddingModel}，供 RAG 流水线使用。
 */
public class HealthKbEmbeddingModel implements EmbeddingModel {

    private final VectorizeService vectorizeService;

    public HealthKbEmbeddingModel(VectorizeService vectorizeService) {
        this.vectorizeService = vectorizeService;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<Embedding> out = new ArrayList<>(segments == null ? 0 : segments.size());
        if (segments != null) {
            for (TextSegment segment : segments) {
                out.add(Embedding.from(vectorizeService.embed(segment == null ? "" : segment.text())));
            }
        }
        return Response.from(out);
    }

    @Override
    public int dimension() {
        return vectorizeService.dimension();
    }

    @Override
    public String modelName() {
        return vectorizeService.semantic() ? "healthkb-semantic" : "healthkb-hash-fallback";
    }
}
