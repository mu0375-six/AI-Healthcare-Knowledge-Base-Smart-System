package com.healthkb.rag;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component("memoryVectorStore")
public class InMemoryVectorStore implements VectorStore {

    private final ConcurrentHashMap<Long, Record> records = new ConcurrentHashMap<>();

    private final VectorizeService vectorizeService;

    public InMemoryVectorStore(@Lazy VectorizeService vectorizeService) {
        this.vectorizeService = vectorizeService;
    }

    @Override
    public void upsert(Long chunkId, Long documentId, float[] vector, String content,
                       String title, String category, String source) {
        records.put(chunkId, new Record(chunkId, documentId, vector, content, title, category, source));
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        records.entrySet().removeIf(e -> documentId.equals(e.getValue().documentId));
    }

    @Override
    public List<ScoredChunk> search(float[] query, int topK) {
        if (query == null || topK <= 0 || records.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> scored = new ArrayList<>(records.size());
        for (Record r : records.values()) {
            float s = cosine(query, r.vector);
            scored.add(new ScoredChunk(r.chunkId, r.documentId, r.content, r.title, r.category, r.source, s));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    @Override
    public int size() {
        return records.size();
    }

    @Override
    public void clear() {
        records.clear();
    }

    @Override
    public VectorStoreInfo info() {
        return new VectorStoreInfo("memory", true, records.size(), vectorizeService.dimension(), "", "进程内余弦检索，重启后重建");
    }

    private static float cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
        }
        return (float) dot;
    }

    private record Record(Long chunkId, Long documentId, float[] vector, String content,
                          String title, String category, String source) {
    }
}
