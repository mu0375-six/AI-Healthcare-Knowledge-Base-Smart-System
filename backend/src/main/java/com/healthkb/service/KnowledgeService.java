package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.healthkb.common.AppException;
import com.healthkb.dto.KnowledgeDtos;
import com.healthkb.dto.PageResult;
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
import java.util.HashMap;
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

    /** 分页版：后台知识库列表不再全量返回。 */
    public PageResult<KbDocument> listDocuments(int page, int size) {
        Page<KbDocument> p = documentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<KbDocument>().orderByDesc(KbDocument::getCreatedAt)
                        // 同秒并列时以 id 兜底，保证翻页顺序稳定
                        .orderByDesc(KbDocument::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
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

    /**
     * 首页「医学小知识」：从已入库的权威文档里取若干条，附首段摘要。
     *
     * 内容源就是知识库本身（世界卫生组织中文实况报道、国家卫生健康委
     * 公开文本，见 OfficialKnowledgeService），不另外去抓外部网页 ——
     * 首页展示的每一条都必须是问答时能被引用的同一份语料，否则就成了
     * 与产品无关的装饰。
     *
     * 只读、无需管理员权限。
     */
    public List<Map<String, Object>> highlights(int limit) {
        int n = Math.max(1, Math.min(limit, 12));
        List<KbDocument> docs = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                // 优先展示带来源出处的权威文档，演示数据排在后面
                .isNotNull(KbDocument::getSourceUrl)
                .orderByDesc(KbDocument::getCreatedAt)
                .orderByDesc(KbDocument::getId)
                .last("limit " + n));
        List<Map<String, Object>> out = new ArrayList<>();
        for (KbDocument d : docs) {
            // 取前几片而不是只取第一片：首片常常是"来源/原文"元信息头，
            // 抓取自网页的文档还会带一段导航栏残留，都不是能读的正文
            List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getDocumentId, d.getId())
                    .orderByAsc(KbChunk::getOrdinal)
                    .last("limit 4"));
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("title", d.getTitle());
            m.put("category", d.getCategory());
            m.put("publisher", d.getPublisher());
            m.put("sourceUrl", d.getSourceUrl());
            m.put("excerpt", firstReadable(chunks, d.getTitle()));
            out.add(m);
        }
        return out;
    }

    /** 网页抓取残留的导航/页脚文案，出现即整句丢弃。 */
    private static final String[] BOILERPLATE = {
            "Skip to main content", "When autocomplete results", "Select language",
            "All topics", "Newsroom", "Fact sheets", "分享", "打印", "字体大小",
            "返回顶部", "无障碍", "网站地图", "阅读时间", "©", "图片来源"
    };

    /**
     * 从前几片里挑出第一段像正文的中文句子。
     * 判定标准：去掉元信息与导航残留后，含句号、且有足够长度。
     */
    private String firstReadable(List<KbChunk> chunks, String title) {
        for (KbChunk c : chunks) {
            if (c == null || c.getContent() == null) {
                continue;
            }
            for (String para : c.getContent().split("\r?\n")) {
                String line = clean(para, title);
                if (line.length() < 24 || !line.contains("。")) {
                    continue;
                }
                return trimTo(line);
            }
        }
        // 兜底：逐行过滤把整篇都滤没了（图片版权行、页眉页脚混排时会这样），
        // 改为按句号切，挑第一句像正文的中文句子
        for (KbChunk c : chunks) {
            if (c == null || c.getContent() == null) {
                continue;
            }
            String flat = c.getContent().replaceAll("https?://\\S+", " ").replaceAll("\\s+", " ").trim();
            for (String sentence : flat.split("(?<=。)")) {
                String t = sentence.trim();
                // 噪声（图片版权、日期、"阅读时间"、小标题）常与正文首句连在一行，
                // 整句丢弃会把这篇文章的摘要弄丢 —— 改为切掉最后一个噪声标记之前的部分
                t = stripNoisePrefix(t);
                // 至少 16 个字、且中文占多数，才当成正文
                if (t.length() >= 16 && t.endsWith("。") && countHan(t) * 2 >= t.length()) {
                    return trimTo(t);
                }
            }
        }
        return "";
    }

    /** 正文首句前的噪声标记，出现即从最后一个之后开始取。 */
    private static final String[] NOISE_MARKERS = {
            "重要事实", "关键事实", "概述", "要点", "阅读时间", "图片来源", "来源", "©"
    };

    private static String stripNoisePrefix(String sentence) {
        int cut = -1;
        for (String m : NOISE_MARKERS) {
            int i = sentence.lastIndexOf(m);
            if (i >= 0) {
                cut = Math.max(cut, i + m.length());
            }
        }
        return cut > 0 && cut < sentence.length() ? sentence.substring(cut).trim() : sentence;
    }

    private static int countHan(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.codePointAt(i)) == Character.UnicodeScript.HAN) {
                n++;
            }
        }
        return n;
    }

    private String clean(String raw, String title) {
        String line = raw.replaceAll("[#*`>]", " ")
                .replaceAll("https?://\\S+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // 「来源：…」「原文：…」这类元信息头不是正文
        if (line.startsWith("来源：") || line.startsWith("原文：") || line.startsWith("来源:")) {
            return "";
        }
        for (String b : BOILERPLATE) {
            if (line.contains(b)) {
                return "";
            }
        }
        // 正文首行常常把标题再念一遍
        if (title != null && line.startsWith(title)) {
            line = line.substring(title.length()).trim();
        }
        // WHO 实况报道会把「重要事实」这类小标题和首句粘在一起
        for (String h : new String[]{"重要事实", "概述", "关键事实", "要点"}) {
            if (line.startsWith(h)) {
                line = line.substring(h.length()).trim();
            }
        }
        return line;
    }

    /** 卡片上只放两三行：优先切在句号处。 */
    private String trimTo(String line) {
        if (line.length() <= 76) {
            return line;
        }
        int cut = line.indexOf('。', 36);
        if (cut > 0 && cut < 110) {
            return line.substring(0, cut + 1);
        }
        return line.substring(0, 76) + "…";
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
