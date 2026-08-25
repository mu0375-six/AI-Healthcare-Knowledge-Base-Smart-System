package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.healthkb.entity.KbDocument;
import com.healthkb.mapper.KbDocumentMapper;
import com.healthkb.rag.OfficialHtmlCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialKnowledgeService {

    private final KnowledgeService knowledgeService;
    private final KbDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.kb.allow-network:true}")
    private boolean allowNetwork;

    @Value("${app.kb.timeout-seconds:18}")
    private int timeoutSeconds;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public SyncResult sync(boolean replaceDemo) {
        SyncResult result = new SyncResult();
        if (replaceDemo) {
            result.removedDemo = knowledgeService.deleteDemoDocuments();
        }
        JsonNode catalog;
        try {
            catalog = readCatalog();
        } catch (Exception e) {
            result.errors.add("无法读取权威源目录: " + e.getMessage());
            return result;
        }
        // 先串行走一遍目录，挑出真正需要联网的条目（已入库的直接跳过）。
        List<Pending> pending = new ArrayList<>();
        for (JsonNode item : catalog) {
            String title = item.path("title").asText("");
            String url = item.path("url").asText("");
            if (title.isBlank() || url.isBlank()) {
                continue;
            }
            if (existsByUrl(url)) {
                result.skipped++;
                continue;
            }
            pending.add(new Pending(title, url,
                    item.path("publisher").asText(""),
                    item.path("category").asText("疾病指南"),
                    item.path("snapshot").asText("")));
        }

        // 抓取并发进行：源有十几个，每个超时 18 秒，串行时首次启动最坏要等好几分钟。
        // 只并行网络这一段，入库仍然串行 —— 并发写库收益不大，却要额外处理事务与顺序。
        Map<String, String> fetched = allowNetwork ? fetchAll(pending, result) : Map.of();

        for (Pending item : pending) {
            String body = fetched.get(item.url());
            String origin = body == null ? "snapshot" : "live";
            if (body == null) {
                body = readSnapshot(item.snapshot());
            }
            if (body == null || body.isBlank()) {
                result.failed++;
                result.errors.add(item.title() + " 无可用正文");
                continue;
            }
            String source = item.publisher() + " · " + item.url();
            knowledgeService.persistDocument(item.title(), item.category(), source, null,
                    body, item.url(), item.publisher());
            result.titles.add(item.title() + " [" + origin + "]");
            if ("live".equals(origin)) {
                result.fetched++;
            } else {
                result.fromSnapshot++;
            }
        }
        return result;
    }

    /**
     * 并发抓取待入库的源，返回 url -> 正文。抓不到的条目不出现在结果里，
     * 由调用方回退到仓库内的快照。
     *
     * <p>线程数取源数量与 6 的较小值：目标是把「串行 N×超时」压成「一轮超时」，
     * 再高的并发对十几个源没有意义，反而容易被对端限流。
     */
    private Map<String, String> fetchAll(List<Pending> pending, SyncResult result) {
        if (pending.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new ConcurrentHashMap<>();
        int threads = Math.min(6, pending.size());
        // ExecutorService 到 Java 19 才实现 AutoCloseable，本项目 target 17，用 finally 关
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "kb-sync");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Pending item : pending) {
                futures.add(pool.submit(() -> {
                    try {
                        String body = fetchText(item.url());
                        if (body != null && body.length() >= 200) {
                            out.put(item.url(), body);
                        }
                    } catch (Exception e) {
                        log.warn("拉取权威源失败 {}: {}", item.url(), e.getMessage());
                        synchronized (result.errors) {
                            result.errors.add(item.title() + " 在线拉取失败，改用快照");
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                    // 单个源失败已在任务内记录，这里不再重复处理
                }
            }
        } finally {
            pool.shutdown();
        }
        return out;
    }

    private record Pending(String title, String url, String publisher, String category, String snapshot) {
    }

    public boolean hasOfficialDocuments() {
        Long n = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .isNotNull(KbDocument::getSourceUrl)
                .ne(KbDocument::getSourceUrl, ""));
        return n != null && n > 0;
    }

    private boolean existsByUrl(String url) {
        Long n = documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getSourceUrl, url));
        return n != null && n > 0;
    }

    private String fetchText(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("User-Agent", "HealthKbOfficialImporter/1.0 (education; sources=WHO/NHC)")
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return OfficialHtmlCleaner.toPlainText(response.body());
    }

    private JsonNode readCatalog() throws Exception {
        try (InputStream in = new ClassPathResource("data/official-sources.json").getInputStream()) {
            return objectMapper.readTree(in);
        }
    }

    private String readSnapshot(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try (InputStream in = new ClassPathResource("data/official/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static class SyncResult {
        public int fetched;
        public int fromSnapshot;
        public int skipped;
        public int failed;
        public int removedDemo;
        public List<String> titles = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
    }
}
