package com.healthkb.rag;

import java.util.List;

public interface VectorStore {

    void upsert(Long chunkId, Long documentId, float[] vector, String content,
                String title, String category, String source);

    void deleteByDocumentId(Long documentId);

    List<ScoredChunk> search(float[] query, int topK);

    int size();

    void clear();

    default void flush() {
    }

    default VectorStoreInfo info() {
        return new VectorStoreInfo("memory", true, size(), 0, "", "in-memory");
    }
}
