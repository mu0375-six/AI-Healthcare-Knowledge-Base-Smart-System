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
import java.util.Comparator;
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

    /** 重排时词法特异度占的权重，其余给向量分。仅在真实 embedding 模式下生效。 */
    @Value("${app.rag.rerank.lexical-weight:0.3}")
    private double lexicalWeight;

    /**
     * 重排后的保留阈值：综合分低于它就判定「知识库对不上」。
     * 该值与 embedding 模型的分数分布有关，换模型后需重新标定 ——
     * 用 GET /api/knowledge/vectors/inspect?q=... 看 combined 一列即可。
     */
    @Value("${app.rag.rerank.min-score:0.55}")
    private double rerankMinScore;

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
        // 检索模式与两个旋钮一并回传：换 embedding 模型后照着 combined 一列重新标定阈值
        out.put("mode", vectorizeService.semantic() ? "rerank" : "lexical-filter");
        out.put("lexicalWeight", lexicalWeight);
        out.put("rerankMinScore", rerankMinScore);
        out.put("queryTerms", queryTerms(query));
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
            row.put("score", round4(h.getScore()));
            row.put("lexicalHit", sharesTerm(keys, h));
            row.put("lexicalScore", round4(lexicalScore(keys, h)));
            row.put("combined", round4(combinedScore(h, keys, lexicalWeight)));
            rows.add(row);
        }
        return rows;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    public List<ScoredChunk> retrieve(String query) {
        return retrieve(query, topK);
    }

    public List<ScoredChunk> retrieve(String query, int k) {
        // LangChain4j 的 Query 不接受空串，空查询直接返回空结果
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<ScoredChunk> hits = recall(query);
        List<String> keys = queryTerms(query);
        // 两种模式分开处理：
        // 哈希兜底时向量分不含语义，相关性只能靠字面命中兜住，保持硬过滤；
        // 换成真实 embedding 后向量分可信，字面命中降级为重排信号 —— 否则
        // 「心梗」永远匹配不上正文里的「心肌梗死」，语义检索的收益会被这一步抵消掉。
        return vectorizeService.semantic() ? rerank(hits, keys, k) : lexicalFilter(hits, keys, k);
    }

    /** 召回：交给 LangChain4j 的 ContentRetriever（向量检索 → Milvus/内存库）。 */
    private List<ScoredChunk> recall(String query) {
        List<ScoredChunk> hits = new ArrayList<>();
        for (Content content : contentRetriever.retrieve(Query.from(query))) {
            Object score = content.metadata().get(ContentMetadata.SCORE);
            double s = score instanceof Number n ? n.doubleValue() : 0d;
            hits.add(VectorStoreEmbeddingStore.toChunk(content.textSegment(), s));
        }
        return hits;
    }

    /** 哈希兜底模式：字面不沾边的一律丢弃，宁可少召回也不要串台。 */
    private static List<ScoredChunk> lexicalFilter(List<ScoredChunk> hits, List<String> keys, int k) {
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

    /** 真实 embedding 模式：按综合分重排，低于阈值才判定检索不到。 */
    private List<ScoredChunk> rerank(List<ScoredChunk> hits, List<String> keys, int k) {
        List<ScoredChunk> ranked = new ArrayList<>(hits);
        ranked.sort(Comparator
                .comparingDouble((ScoredChunk h) -> combinedScore(h, keys, lexicalWeight))
                .reversed());
        List<ScoredChunk> kept = new ArrayList<>();
        for (ScoredChunk hit : ranked) {
            if (combinedScore(hit, keys, lexicalWeight) < rerankMinScore) {
                break; // 已按综合分降序，后面只会更低
            }
            kept.add(hit);
            if (kept.size() >= k) {
                break;
            }
        }
        return kept;
    }

    /** 综合分 = 向量分 ×(1-w) + 词法特异度 ×w。 */
    static double combinedScore(ScoredChunk hit, List<String> keys, double lexicalWeight) {
        if (hit == null) {
            return 0d;
        }
        double w = Math.min(1.0, Math.max(0.0, lexicalWeight));
        return hit.getScore() * (1 - w) + lexicalScore(keys, hit) * w;
    }

    /**
     * 词法特异度 0..1：取命中词里最长的那个，按 4 字封顶归一。
     *
     * <p>用「最长命中」而不是「命中数量」，是因为 {@link #queryTerms} 会为长词
     * 额外产出一批 2-3 字 n-gram，按数量算会被这些碎片稀释 ——
     * 命中「心肌梗死」显然比命中「心肌」更能说明这块内容对得上。
     */
    static double lexicalScore(List<String> keys, ScoredChunk hit) {
        if (keys == null || keys.isEmpty() || hit == null) {
            return 0d;
        }
        String text = (hit.getTitle() == null ? "" : hit.getTitle()) + "\n"
                + (hit.getContent() == null ? "" : hit.getContent());
        int longest = 0;
        for (String k : keys) {
            if (k.length() >= 2 && k.length() > longest && text.contains(k)) {
                longest = k.length();
            }
        }
        return Math.min(1.0, longest / 4.0);
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
        // 展开医学别名：让「心梗」也能匹配到正文里的「心肌梗死」
        return MedicalSynonyms.expand(terms);
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
        // 真实 embedding 下这些引用来自已经过重排阈值的知识块，相关性在检索阶段就判过了；
        // 再按字面复筛一遍，只会把同义表述的正确出处误杀。
        if (vectorizeService.semantic()) {
            return List.copyOf(raw);
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
