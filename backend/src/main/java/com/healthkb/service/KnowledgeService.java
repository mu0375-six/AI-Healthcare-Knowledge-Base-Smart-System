package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.common.AppException;
import com.healthkb.dto.KnowledgeDtos;
import com.healthkb.entity.KbChunk;
import com.healthkb.entity.KbDocument;
import com.healthkb.mapper.KbChunkMapper;
import com.healthkb.mapper.KbDocumentMapper;
import com.healthkb.rag.DocumentParser;
import com.healthkb.rag.RagService;
import com.healthkb.rag.ScoredChunk;
import com.healthkb.rag.TextChunker;
import com.healthkb.rag.VectorStoreInfo;
import com.healthkb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final DocumentParser documentParser;
    private final TextChunker textChunker;
    private final RagService ragService;

    public List<KbDocument> listDocuments() {
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .orderByDesc(KbDocument::getCreatedAt));
    }

    @Transactional
    public KbDocument addText(KnowledgeDtos.TextRequest req) {
        return persistDocument(
                req.getTitle().trim(),
                emptyToDefault(req.getCategory(), "疾病指南"),
                emptyToDefault(req.getSource(), "后台录入"),
                null,
                req.getContent());
    }

    @Transactional
    public KbDocument upload(MultipartFile file, String title, String category, String source, String extractedText) {
        String text = documentParser.extractText(file, extractedText);
        if (text.isBlank()) {
            throw AppException.badRequest("未能从文件中提取到文本，请粘贴正文或使用含文字的 PDF/Word");
        }
        String name = file.getOriginalFilename();
        String docTitle = (title == null || title.isBlank())
                ? (name == null ? "未命名文档" : name.replaceAll("\\.[^.]+$", ""))
                : title.trim();
        return persistDocument(
                docTitle,
                emptyToDefault(category, "疾病指南"),
                emptyToDefault(source, name),
                name,
                text);
    }

    @Transactional
    public void delete(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw AppException.notFound("知识库文档不存在");
        }
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, id));
        documentMapper.deleteById(id);
        ragService.removeDocument(id);
    }

    public Map<String, Object> vectorInspect(String q) {
        return ragService.inspect(q);
    }

    public VectorStoreInfo vectorStatus() {
        return ragService.storeInfo();
    }

    public int reindex() {
        ragService.rebuildFromDatabase();
        return ragService.vectorCount();
    }

    public List<Map<String, Object>> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        List<ScoredChunk> hits = ragService.retrieve(q, 8);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScoredChunk h : hits) {
            rows.add(Map.of(
                    "title", nvl(h.getTitle()),
                    "category", nvl(h.getCategory()),
                    "source", nvl(h.getSource()),
                    "snippet", brief(h.getContent(), 180),
                    "score", h.getScore()
            ));
        }
        return rows;
    }

    public List<String> terms() {
        Set<String> terms = new LinkedHashSet<>();
        for (KbDocument d : documentMapper.selectList(null)) {
            if (d.getTitle() != null) {
                terms.add(d.getTitle());
            }
        }
        terms.addAll(List.of(
                "高血压", "糖尿病", "普通感冒", "慢性胃炎", "冠心病",
                "阿司匹林", "二甲双胍", "氨氯地平",
                "心血管内科", "内分泌科", "呼吸内科", "消化内科", "神经内科",
                "皮肤科", "儿科", "妇产科", "急诊科", "全科",
                "空腹血糖", "收缩压", "舒张压", "胸痛", "低血糖"
        ));
        return new ArrayList<>(terms);
    }

    public int deleteDemoDocuments() {
        List<KbDocument> all = documentMapper.selectList(null);
        int n = 0;
        for (KbDocument d : all) {
            if (isDemo(d)) {
                delete(d.getId());
                n++;
            }
        }
        return n;
    }

    public KbDocument persistDocument(String title, String category, String source, String filename, String content) {
        return persistDocument(title, category, source, filename, content, null, null);
    }

    public KbDocument persistDocument(String title, String category, String source, String filename, String content,
                                      String sourceUrl, String publisher) {
        KbDocument doc = new KbDocument();
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setSource(source);
        doc.setSourceUrl(sourceUrl);
        doc.setPublisher(publisher);
        doc.setFilename(filename);
        try {
            doc.setCreatedBy(SecurityUtils.currentUserId());
        } catch (Exception ignored) {
            doc.setCreatedBy(0L);
        }
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        List<String> pieces = textChunker.chunk(content);
        int ord = 0;
        for (String piece : pieces) {
            KbChunk chunk = new KbChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setContent(piece);
            chunk.setOrdinal(ord++);
            chunkMapper.insert(chunk);
            ragService.indexChunk(chunk, doc);
        }
        return doc;
    }

    private static boolean isDemo(KbDocument d) {
        String source = d.getSource() == null ? "" : d.getSource();
        String publisher = d.getPublisher() == null ? "" : d.getPublisher();
        return source.contains("演示") || publisher.contains("演示");
    }

    private static String emptyToDefault(String v, String dft) {
        return v == null || v.isBlank() ? dft : v.trim();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String brief(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
