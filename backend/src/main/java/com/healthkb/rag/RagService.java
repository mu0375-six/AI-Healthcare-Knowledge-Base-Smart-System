package com.healthkb.rag;

import com.healthkb.entity.KbChunk;
import com.healthkb.entity.KbDocument;
import com.healthkb.mapper.KbChunkMapper;
import com.healthkb.mapper.KbDocumentMapper;
import com.healthkb.rag.lc.VectorStoreEmbeddingStore;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorizeService vectorizeService;
    private final ContentRetriever contentRetriever;
    private final VectorStore vectorStore;
    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;

    @Value("${app.rag.top-k:5}")
    private int topK;

    public int vectorCount() {
        return vectorStore.size();
    }

    public VectorStoreInfo storeInfo() {
        return vectorStore.info();
    }

    public int embeddingDim() {
        return vectorizeService.dimension();
    }

    public Map<String, Object> inspect(String query) {
        long t0 = System.currentTimeMillis();
        List<ScoredChunk> raw = vectorStore.search(vectorizeService.embed(query), 12);
        List<ScoredChunk> kept = retrieve(query, 8);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("elapsedMs", System.currentTimeMillis() - t0);
        out.put("store", storeInfo());
        out.put("dim", vectorizeService.dimension());
        out.put("semantic", vectorizeService.semantic());
        out.put("rawHits", toMaps(raw, query));
        out.put("keptHits", toMaps(kept, query));
        return out;
    }

    private List<Map<String, Object>> toMaps(List<ScoredChunk> hits, String query) {
        List<String> keys = queryTerms(query);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScoredChunk h : hits) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunkId", h.getChunkId());
            row.put("documentId", h.getDocumentId());
            row.put("title", h.getTitle() == null ? "" : h.getTitle());
            row.put("category", h.getCategory() == null ? "" : h.getCategory());
            row.put("source", h.getSource() == null ? "" : h.getSource());
            String sn = h.getContent() == null ? "" : h.getContent();
            row.put("snippet", sn.length() > 180 ? sn.substring(0, 180) + "…" : sn);
            row.put("score", Math.round(h.getScore() * 10000.0) / 10000.0);
            row.put("lexicalHit", sharesTerm(keys, h));
            rows.add(row);
        }
        return rows;
    }

    public List<ScoredChunk> retrieve(String query) {
        return retrieve(query, topK);
    }

    public List<ScoredChunk> retrieve(String query, int k) {
        // LangChain4j 的 Query 不接受空串，空查询直接返回空结果
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 召回交给 LangChain4j 的 ContentRetriever（向量检索 → Milvus/内存库），
        // 再用关键词精排截断到 k：哈希兜底模式下这一步是召回质量的主要保障。
        List<ScoredChunk> hits = new ArrayList<>();
        for (Content content : contentRetriever.retrieve(Query.from(query))) {
            Object score = content.metadata().get(ContentMetadata.SCORE);
            double s = score instanceof Number n ? n.doubleValue() : 0d;
            hits.add(VectorStoreEmbeddingStore.toChunk(content.textSegment(), s));
        }
        List<String> keys = queryTerms(query);
        List<ScoredChunk> filtered = new ArrayList<>();
        for (ScoredChunk hit : hits) {
            if (!sharesTerm(keys, hit)) {
                continue;
            }
            filtered.add(hit);
            if (filtered.size() >= k) {
                break;
            }
        }
        return filtered;
    }

    static List<String> queryTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        StringBuilder han = new StringBuilder();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                han.append(c);
            } else {
                splitHan(han.toString(), terms);
                han.setLength(0);
                if (Character.isLetterOrDigit(c)) {
                    StringBuilder lat = new StringBuilder().append(c);
                    while (i + 1 < query.length() && Character.isLetterOrDigit(query.charAt(i + 1))) {
                        lat.append(query.charAt(++i));
                    }
                    String w = lat.toString().toLowerCase();
                    if (w.length() >= 2 && !STOP.contains(w)) {
                        terms.add(w);
                    }
                }
            }
        }
        splitHan(han.toString(), terms);
        return new ArrayList<>(terms);
    }

    private static void splitHan(String run, Set<String> terms) {
        if (run == null || run.isEmpty()) {
            return;
        }
        String remaining = run;
        for (String stop : STOP) {
            remaining = remaining.replace(stop, " ");
        }
        for (String part : remaining.split("\\s+")) {
            if (part.length() < 2 || STOP.contains(part)) {
                continue;
            }
            terms.add(part);
            if (part.length() >= 4) {
                for (int n = 2; n <= 3; n++) {
                    for (int i = 0; i + n <= part.length(); i++) {
                        String g = part.substring(i, i + n);
                        if (!STOP.contains(g)) {
                            terms.add(g);
                        }
                    }
                }
            }
        }
    }

    public List<Citation> visibleCitations(String question, List<Citation> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> keys = queryTerms(question);
        List<Citation> kept = new ArrayList<>();
        for (Citation c : raw) {
            if (citationMatchesQuestion(keys, c)) {
                kept.add(c);
            }
        }
        return kept;
    }

    static boolean citationMatchesQuestion(List<String> keys, Citation c) {
        if (keys.isEmpty() || c == null) {
            return false;
        }
        String blob = (c.getTitle() == null ? "" : c.getTitle()) + "\n"
                + (c.getSnippet() == null ? "" : c.getSnippet());
        for (String k : keys) {
            if (k.length() >= 2 && blob.contains(k)) {
                return true;
            }
        }
        return false;
    }

    static boolean sharesTerm(List<String> keys, ScoredChunk hit) {
        if (keys.isEmpty()) {
            return false;
        }
        String text = ((hit.getTitle() == null ? "" : hit.getTitle()) + "\n"
                + (hit.getContent() == null ? "" : hit.getContent()));
        for (String k : keys) {
            if (k.length() >= 2 && text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> STOP = Set.of(
            "最近", "有点", "并且", "怎么", "什么", "哪些", "一下", "还是", "比较", "感觉",
            "注意", "事项", "这个", "一个", "可以", "需要", "如果", "或者", "以及", "进行",
            "出现", "相关", "问题", "情况", "时候", "之后", "之前", "我们", "自己", "怎么办",
            "注意事项", "请问", "想问", "咨询", "了解", "是否", "怎样", "为何",
            "因为", "所以", "但是", "然后", "现在", "今天", "昨天", "有些", "一点");

    public List<Citation> toCitations(List<ScoredChunk> chunks) {
        Map<String, Citation> uniq = new LinkedHashMap<>();
        for (ScoredChunk c : chunks) {
            String key = c.getTitle() + "|" + c.getCategory();
            if (uniq.containsKey(key)) {
                continue;
            }
            String snippet = c.getContent();
            if (snippet != null && snippet.length() > 160) {
                snippet = snippet.substring(0, 160) + "…";
            }
            uniq.put(key, new Citation(c.getTitle(), c.getSource(), snippet, c.getCategory()));
        }
        return new ArrayList<>(uniq.values());
    }

    public void indexChunk(KbChunk chunk, KbDocument doc) {
        float[] vec = vectorizeService.embed((doc.getTitle() == null ? "" : doc.getTitle()) + " " + chunk.getContent());
        vectorStore.upsert(chunk.getId(), doc.getId(), vec, chunk.getContent(),
                doc.getTitle(), doc.getCategory(), doc.getSource());
    }

    public void removeDocument(Long documentId) {
        vectorStore.deleteByDocumentId(documentId);
    }

    public void rebuildFromDatabase() {
        vectorStore.clear();
        List<KbChunk> chunks = chunkMapper.selectList(null);
        if (chunks.isEmpty()) {
            return;
        }
        Map<Long, KbDocument> docs = new LinkedHashMap<>();
        for (KbDocument d : documentMapper.selectList(null)) {
            docs.put(d.getId(), d);
        }
        for (KbChunk chunk : chunks) {
            KbDocument doc = docs.get(chunk.getDocumentId());
            if (doc != null) {
                indexChunk(chunk, doc);
            }
        }
        vectorStore.flush();
    }
}
