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
        for (JsonNode item : catalog) {
            String title = item.path("title").asText("");
            String url = item.path("url").asText("");
            String publisher = item.path("publisher").asText("");
            String category = item.path("category").asText("疾病指南");
            String snapshot = item.path("snapshot").asText("");
            if (title.isBlank() || url.isBlank()) {
                continue;
            }
            if (existsByUrl(url)) {
                result.skipped++;
                continue;
            }
            String body = null;
            String origin;
            if (allowNetwork) {
                try {
                    body = fetchText(url);
                    if (body != null && body.length() >= 200) {
                        origin = "live";
                    } else {
                        body = null;
                    }
                } catch (Exception e) {
                    log.warn("拉取权威源失败 {}: {}", url, e.getMessage());
                    result.errors.add(title + " 在线拉取失败，改用快照");
                    body = null;
                }
            }
            if (body == null) {
                body = readSnapshot(snapshot);
                origin = "snapshot";
            } else {
                origin = "live";
            }
            if (body == null || body.isBlank()) {
                result.failed++;
                result.errors.add(title + " 无可用正文");
                continue;
            }
            String source = publisher + " · " + url;
            knowledgeService.persistDocument(title, category, source, null, body, url, publisher);
            result.titles.add(title + " [" + origin + "]");
            if ("live".equals(origin)) {
                result.fetched++;
            } else {
                result.fromSnapshot++;
            }
        }
        return result;
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
