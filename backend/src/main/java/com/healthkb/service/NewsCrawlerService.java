package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.entity.NewsItem;
import com.healthkb.mapper.NewsItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 首页健康新闻的采集：应用启动完成后与每 6 小时各爬一次
 * 世界卫生组织中文新闻室（列表 JSON + 文章正文 + 配图下载到本地）。
 * 网络不可用或一无所获时，落库内置科普快照兜底，首页永远不为空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCrawlerService {

    private static final String LIST_API = "https://www.who.int/api/hubs/newsitems"
            + "?sf_site=15210d59-ad60-47ff-a542-7ed76645f0c7"
            + "&sf_provider=OpenAccessDataProvider&sf_culture=zh"
            + "&%24orderby=PublicationDateAndTime%20desc"
            + "&%24select=Title,ItemDefaultUrl,FormatedDate,ThumbnailUrl&%24top=12";
    private static final String ARTICLE_BASE = "https://www.who.int/zh/news/item";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int KEEP_MAX = 30;
    private static final long MAX_IMAGE_BYTES = 3L * 1024 * 1024;
    /** 正文段落的最短长度：再短的基本是导航、按钮、版权行。 */
    private static final int MIN_PARAGRAPH_CHARS = 30;

    private static final Pattern P_TAG = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern OG_DESC_1 = Pattern.compile("<meta[^>]*property=\"og:description\"[^>]*content=\"([^\"]*)\"", Pattern.DOTALL);
    private static final Pattern OG_DESC_2 = Pattern.compile("<meta[^>]*content=\"([^\"]*)\"[^>]*property=\"og:description\"", Pattern.DOTALL);
    private static final Pattern OG_IMAGE_1 = Pattern.compile("<meta[^>]*property=\"og:image\"[^>]*content=\"([^\"]*)\"", Pattern.DOTALL);
    private static final Pattern OG_IMAGE_2 = Pattern.compile("<meta[^>]*content=\"([^\"]*)\"[^>]*property=\"og:image\"", Pattern.DOTALL);
    private static final Pattern CN_DATE = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");

    private final NewsItemMapper newsMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.news.allow-network:true}")
    private boolean allowNetwork;
    @Value("${app.news.enabled:true}")
    private boolean enabled;
    @Value("${app.news.image-dir:./data/news-images}")
    private String imageDir;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 单线程守护池：启动爬取不阻塞就绪，文章级抓取小并发。 */
    private final ExecutorService startupPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "news-crawl-startup");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "news-crawl-fetch");
        t.setDaemon(true);
        return t;
    });

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        startupPool.submit(() -> refresh("启动"));
    }

    @Scheduled(fixedDelay = 6 * 3600_000L, initialDelay = 6 * 3600_000L)
    public void scheduledRefresh() {
        refresh("定时");
    }

    /** 全量刷新：库里已有的按 source_url 跳过，只增量补新文章，最后裁掉超出上限的旧条目。 */
    public synchronized void refresh(String trigger) {
        if (!enabled) {
            return;
        }
        if (!allowNetwork) {
            seedFallback(trigger);
            return;
        }
        try {
            List<Pending> items = fetchList();
            int inserted = 0;
            if (!items.isEmpty()) {
                Set<String> existing = new HashSet<>();
                for (NewsItem n : newsMapper.selectList(null)) {
                    existing.add(n.getSourceUrl());
                }
                List<Pending> fresh = items.stream().filter(p -> !existing.contains(p.url())).toList();
                Map<String, Article> fetched = fetchArticles(fresh);
                for (Pending p : fresh) {
                    Article a = fetched.get(p.url());
                    if (a == null || a.paragraphs.isEmpty()) {
                        continue;
                    }
                    if (insert(p, a)) {
                        inserted++;
                    }
                }
            }
            if (inserted > 0) {
                trim();
                log.info("[{}] 健康新闻抓取完成：新增 {} 条", trigger, inserted);
            } else {
                // 列表抓不动或正文全失败：只要库里还有货就不动它，空库才落快照
                if (newsMapper.selectCount(null) == 0) {
                    seedFallback(trigger);
                } else {
                    log.warn("[{}] 健康新闻本次无新增（网络或页面结构变化），保留现有 {} 条", trigger, newsMapper.selectCount(null));
                }
            }
        } catch (Exception e) {
            log.warn("[{}] 健康新闻抓取失败：{}", trigger, e.getMessage());
            seedFallback(trigger);
        }
    }

    // ---------------------------------------------------------------- list

    private List<Pending> fetchList() {
        String body = httpSend(LIST_API);
        if (body == null) {
            return List.of();
        }
        JsonNode arr = objectMapper.readTree(body).path("value");
        List<Pending> list = new ArrayList<>();
        for (JsonNode n : arr) {
            String title = n.path("Title").asText("").trim();
            String path = n.path("ItemDefaultUrl").asText("").trim();
            if (title.isEmpty() || path.isEmpty()) {
                continue;
            }
            list.add(new Pending(title, ARTICLE_BASE + path,
                    n.path("FormatedDate").asText(""),
                    n.path("ThumbnailUrl").asText("")));
        }
        return list;
    }

    // ---------------------------------------------------------------- article

    private Map<String, Article> fetchArticles(List<Pending> items) {
        Map<String, Future<Article>> futures = new ConcurrentHashMap<>();
        for (Pending p : items) {
            futures.put(p.url(), fetchPool.submit(() -> fetchArticle(p)));
        }
        Map<String, Article> out = new ConcurrentHashMap<>();
        for (Map.Entry<String, Future<Article>> e : futures.entrySet()) {
            try {
                Article a = e.getValue().get();
                if (a != null) {
                    out.put(e.getKey(), a);
                }
            } catch (Exception ex) {
                log.debug("文章抓取失败 {}: {}", e.getKey(), ex.getMessage());
            }
        }
        return out;
    }

    private Article fetchArticle(Pending p) {
        String html = httpSend(p.url());
        if (html == null) {
            return null;
        }
        Article a = new Article();
        a.summary = unescape(firstMatch(html, OG_DESC_1, OG_DESC_2));
        String ogImage = firstMatch(html, OG_IMAGE_1, OG_IMAGE_2);
        a.imageUrl = ogImage != null ? ogImage : p.thumbnail();
        a.paragraphs = extractParagraphs(html);
        return a;
    }

    /** 只留正文段：够长、以中文为主，且不像版权/订阅行。 */
    private List<String> extractParagraphs(String html) {
        List<String> out = new ArrayList<>();
        Matcher m = P_TAG.matcher(html);
        while (m.find() && out.size() < 12) {
            String text = unescape(m.group(1).replaceAll("<[^>]+>", "")).replaceAll("\\s+", " ").trim();
            if (text.length() < MIN_PARAGRAPH_CHARS || !cjkDominant(text)) {
                continue;
            }
            if (text.contains("©") || text.contains("订阅") || text.contains("相关链接")) {
                continue;
            }
            out.add(text);
        }
        return out;
    }

    private static boolean cjkDominant(String s) {
        int cjk = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                cjk++;
            }
        }
        return cjk * 10 >= s.length() * 3;
    }

    // ---------------------------------------------------------------- persist

    private boolean insert(Pending p, Article a) {
        NewsItem n = new NewsItem();
        n.setTitle(p.title());
        n.setSummary(clip(a.summary, 200));
        String content = String.join("\n\n", a.paragraphs);
        n.setContent(content.length() > 9000 ? content.substring(0, 9000) : content);
        n.setSourceName("世界卫生组织");
        n.setSourceUrl(p.url());
        n.setCategory("健康新闻");
        LocalDate d = parseCnDate(p.dateText());
        n.setPublishedOn(d != null ? d : LocalDate.now());
        n.setCrawledAt(LocalDateTime.now());
        if (a.imageUrl != null && !a.imageUrl.isBlank()) {
            n.setImageName(downloadImage(a.imageUrl, p.url()));
        }
        try {
            return newsMapper.insert(n) > 0;
        } catch (Exception e) {
            log.debug("新闻入库失败 {}: {}", p.title(), e.getMessage());
            return false;
        }
    }

    /** 库容裁剪：只留最新 KEEP_MAX 条。 */
    private void trim() {
        Long count = newsMapper.selectCount(null);
        if (count == null || count <= KEEP_MAX) {
            return;
        }
        List<NewsItem> stale = newsMapper.selectList(new LambdaQueryWrapper<NewsItem>()
                .orderByDesc(NewsItem::getPublishedOn)
                .orderByDesc(NewsItem::getId)
                .last("LIMIT " + (count - KEEP_MAX)));
        for (NewsItem n : stale) {
            newsMapper.deleteById(n.getId());
        }
    }

    /** 兜底快照：联网失败时把内置科普条目落库，首页图文流不为空。 */
    private void seedFallback(String trigger) {
        try {
            if (newsMapper.selectCount(null) > 0) {
                return;
            }
            String body = new String(new ClassPathResource("data/seed-news.json").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (body == null) {
                return;
            }
            int inserted = 0;
            for (JsonNode n : objectMapper.readTree(body)) {
                NewsItem item = new NewsItem();
                item.setTitle(n.path("title").asText());
                item.setSummary(n.path("summary").asText());
                item.setContent(n.path("content").asText());
                item.setBuiltinImage(n.path("builtinImage").asText("health"));
                item.setSourceName(n.path("sourceName").asText("世界卫生组织"));
                item.setSourceUrl(n.path("sourceUrl").asText());
                item.setCategory(n.path("category").asText("健康科普"));
                item.setPublishedOn(LocalDate.now().minusDays(n.path("daysAgo").asInt(0)));
                item.setCrawledAt(LocalDateTime.now());
                if (newsMapper.insert(item) > 0) {
                    inserted++;
                }
            }
            log.warn("[{}] 网络不可用，已落库 {} 条内置科普快照", trigger, inserted);
        } catch (Exception e) {
            log.warn("[{}] 内置科普快照落库失败：{}", trigger, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- image

    /** 配图下载到本地，避免前端热链外站图。失败返回 null，卡片会退化为标题卡。 */
    private String downloadImage(String imageUrl, String key) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", UA)
                    .GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                return null;
            }
            byte[] bytes = resp.body();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                return null;
            }
            String type = resp.headers().firstValue("Content-Type").orElse("");
            if (!type.startsWith("image/")) {
                return null;
            }
            String ext = type.contains("png") ? "png" : type.contains("webp") ? "webp" : "jpg";
            Files.createDirectories(Path.of(imageDir));
            String name = "news-" + Integer.toHexString(Math.abs((key + imageUrl).hashCode())) + "." + ext;
            Path target = Path.of(imageDir, name);
            if (!Files.exists(target)) {
                Files.write(target, bytes);
            }
            return name;
        } catch (Exception e) {
            log.debug("配图下载失败 {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------- helpers

    private String httpSend(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(18))
                    .header("User-Agent", UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            log.debug("请求失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private static String firstMatch(String html, Pattern... patterns) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(html);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static LocalDate parseCnDate(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = CN_DATE.matcher(text);
        if (m.find()) {
            try {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private record Pending(String title, String url, String dateText, String thumbnail) {
    }

    private static class Article {
        String summary;
        String imageUrl;
        List<String> paragraphs = List.of();
    }
}
