package com.healthkb.rag;

import java.util.List;

public interface VectorStore {

    void upsert(Long chunkId, Long documentId, float[] vector, String content,
                String title, String category, String source);

    void deleteByDocumentId(Long documentId);

    List<ScoredChunk> search(float[] query, int topK);

    int size();

    void clear();

    /**
     * 批量读回全部向量与元数据（不含相似度分）。持久化实现（Milvus）用它支撑
     * 「启动时只回灌内存、不重算 embedding」；不支持时抛 UnsupportedOperationException，
     * 调用方须兜底为全量重建。
     */
    default java.util.List<StoredChunk> loadAll() {
        throw new UnsupportedOperationException("loadAll");
    }

    default void flush() {
    }

    default VectorStoreInfo info() {
        return new VectorStoreInfo("memory", true, size(), 0, "", "in-memory");
    }
}
