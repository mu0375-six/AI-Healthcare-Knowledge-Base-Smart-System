package com.healthkb.rag;

/**
 * 带原始向量的知识切片，专供 {@link VectorStore#loadAll()} 批量读回使用 ——
 * 比如启动时把 Milvus 里已算好的向量搬回内存库，全程不需要再调 embedding。
 */
public record StoredChunk(long chunkId, long documentId, float[] vector, String content,
                          String title, String category, String source) {
}
