package com.healthkb.rag.lc;

import com.healthkb.rag.ScoredChunk;
import com.healthkb.rag.VectorStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 把现有 {@link VectorStore}（RoutingVectorStore = Milvus 主存储 + 内存兜底）
 * 适配成 LangChain4j 的 {@link EmbeddingStore}，让 RAG 流水线直接检索 Milvus，
 * 不必额外引入 langchain4j-milvus 模块。
 *
 * <p>切片的业务标识（chunkId / documentId / title / category / source）通过
 * {@link TextSegment} 的 {@link Metadata} 往返传递。
 */
public class VectorStoreEmbeddingStore implements EmbeddingStore<TextSegment> {

    public static final String META_CHUNK_ID = "chunkId";
    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_TITLE = "title";
    public static final String META_CATEGORY = "category";
    public static final String META_SOURCE = "source";

    private final VectorStore delegate;

    public VectorStoreEmbeddingStore(VectorStore delegate) {
        this.delegate = delegate;
    }

    public static TextSegment toSegment(ScoredChunk chunk) {
        Metadata metadata = new Metadata();
        metadata.put(META_CHUNK_ID, chunk.getChunkId() == null ? 0L : chunk.getChunkId());
        metadata.put(META_DOCUMENT_ID, chunk.getDocumentId() == null ? 0L : chunk.getDocumentId());
        metadata.put(META_TITLE, nullToEmpty(chunk.getTitle()));
        metadata.put(META_CATEGORY, nullToEmpty(chunk.getCategory()));
        metadata.put(META_SOURCE, nullToEmpty(chunk.getSource()));
        return TextSegment.from(nullToEmpty(chunk.getContent()), metadata);
    }

    /** 把检索命中的 TextSegment 还原成业务侧的 ScoredChunk。 */
    public static ScoredChunk toChunk(TextSegment segment, double score) {
        Metadata m = segment.metadata();
        return new ScoredChunk(
                readLong(m, META_CHUNK_ID),
                readLong(m, META_DOCUMENT_ID),
                segment.text(),
                m.getString(META_TITLE),
                m.getString(META_CATEGORY),
                m.getString(META_SOURCE),
                (float) score);
    }

    @Override
    public String add(Embedding embedding) {
        throw new UnsupportedOperationException("知识库切片必须携带 TextSegment 元数据");
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw new UnsupportedOperationException("知识库切片必须携带 TextSegment 元数据");
    }

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        Long chunkId = readLong(segment.metadata(), META_CHUNK_ID);
        delegate.upsert(
                chunkId,
                readLong(segment.metadata(), META_DOCUMENT_ID),
                embedding.vector(),
                segment.text(),
                segment.metadata().getString(META_TITLE),
                segment.metadata().getString(META_CATEGORY),
                segment.metadata().getString(META_SOURCE));
        return String.valueOf(chunkId);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw new UnsupportedOperationException("知识库切片必须携带 TextSegment 元数据");
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        List<String> ids = new ArrayList<>(embeddings.size());
        for (int i = 0; i < embeddings.size(); i++) {
            ids.add(add(embeddings.get(i), segments.get(i)));
        }
        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        List<ScoredChunk> hits = delegate.search(request.queryEmbedding().vector(), request.maxResults());
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>(hits.size());
        for (ScoredChunk hit : hits) {
            double score = hit.getScore();
            if (score < request.minScore()) {
                continue;
            }
            matches.add(new EmbeddingMatch<>(
                    score,
                    String.valueOf(hit.getChunkId()),
                    null,
                    toSegment(hit)));
        }
        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public void removeAll() {
        delegate.clear();
    }

    private static Long readLong(Metadata m, String key) {
        Long v = m.getLong(key);
        return v == null ? 0L : v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
