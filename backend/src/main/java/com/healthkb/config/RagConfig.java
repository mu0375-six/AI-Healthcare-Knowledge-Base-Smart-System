package com.healthkb.config;

import com.healthkb.rag.VectorStore;
import com.healthkb.rag.VectorizeService;
import com.healthkb.rag.lc.HealthKbEmbeddingModel;
import com.healthkb.rag.lc.VectorStoreEmbeddingStore;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j RAG 流水线编排：切分 → 向量化 → 检索。
 *
 * <p>向量存储复用项目已有的 {@code RoutingVectorStore}（Milvus 主存储 + 内存兜底），
 * 通过 {@link VectorStoreEmbeddingStore} 适配，因此不需要 langchain4j-milvus 模块。
 */
@Configuration
public class RagConfig {

    /**
     * 检索候选放大到 topK 的 3 倍后交由 RagService 关键词精排、再截断到调用方要的 k。
     * 下限取 24：RagService.retrieve 最大被以 k=8 调用（知识库搜索），
     * 保证候选数不小于改造前的 max(k*3, 12)。
     */
    private static final int RECALL_FACTOR = 3;
    private static final int MIN_RECALL = 24;

    @Bean
    public EmbeddingModel langchainEmbeddingModel(VectorizeService vectorizeService) {
        return new HealthKbEmbeddingModel(vectorizeService);
    }

    @Bean
    public EmbeddingStore<TextSegment> langchainEmbeddingStore(VectorStore vectorStore) {
        return new VectorStoreEmbeddingStore(vectorStore);
    }

    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> store,
                                             EmbeddingModel embeddingModel,
                                             @Value("${app.rag.top-k:5}") int topK,
                                             @Value("${app.rag.min-score:0.0}") double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(Math.max(MIN_RECALL, topK * RECALL_FACTOR))
                .minScore(minScore)
                .build();
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();
    }

    @Bean
    public DocumentSplitter documentSplitter(@Value("${app.rag.chunk-size:600}") int chunkSize,
                                             @Value("${app.rag.chunk-overlap:80}") int overlap) {
        return DocumentSplitters.recursive(chunkSize, overlap);
    }
}
