package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.crypto.digest.DigestUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.healthkb.cache.CacheService;
import com.healthkb.common.AppException;
import com.healthkb.common.EmergencyRules;
import com.healthkb.common.MedicalConstants;
import com.healthkb.dto.PageResult;
import com.healthkb.entity.ChatImage;
import com.healthkb.entity.ChatMessage;
import com.healthkb.entity.ChatSession;
import com.healthkb.mapper.ChatImageMapper;
import com.healthkb.mapper.ChatMessageMapper;
import com.healthkb.mapper.ChatSessionMapper;
import com.healthkb.rag.ChatTurn;
import com.healthkb.rag.Citation;
import com.healthkb.rag.LlmClient;
import com.healthkb.rag.RagService;
import com.healthkb.rag.ScoredChunk;
import com.healthkb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final ChatImageService chatImageService;
    private final ChatImageMapper chatImageMapper;
    private final HealthService healthService;

    @Value("${app.rag.context-turns:6}")
    private int contextTurns;

    @Value("${app.rag.cache-ttl-minutes:10}")
    private int cacheTtlMinutes;

    @Value("${app.rag.context-cache-minutes:30}")
    private int contextCacheMinutes;

    /**
     * 有界 SSE 线程池。cachedThreadPool 每个并发回答占一条线程、阻塞整个 LLM 流式时长，
     * 低速并发就能把线程数刷到任意高。这里封顶 32 并发 + 排队 16，
     * 超出按「稍后再试」拒绝 —— 宁可明确拒绝也不能把进程拖垮。
     */
    private final ExecutorService ssePool = new ThreadPoolExecutor(
            8, 32, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(16),
            r -> {
                Thread t = new Thread(r, "chat-sse");
                t.setDaemon(true);
                return t;
            });

    public ChatSession createSession(String title) {
        ChatSession s = new ChatSession();
        s.setUserId(SecurityUtils.currentUserId());
        s.setTitle(title == null || title.isBlank() ? "新的咨询" : title.trim());
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(s);
        return s;
    }

    public List<ChatSession> listSessions() {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, SecurityUtils.currentUserId())
                .orderByDesc(ChatSession::getUpdatedAt));
    }

    /** 分页版：侧栏只展示最近一页，会话攒多了不再全量拉。 */
    public PageResult<ChatSession> listSessions(int page, int size) {
        Page<ChatSession> p = sessionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, SecurityUtils.currentUserId())
                        .orderByDesc(ChatSession::getUpdatedAt)
                        // updated_at 是秒级 TIMESTAMP，同秒并列时以 id 兜底，
                        // 否则 LIMIT/OFFSET 翻页在同秒记录间可能重复或漏行
                        .orderByDesc(ChatSession::getId));
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    public List<ChatMessage> listMessages(Long sessionId) {
        ChatSession session = requireOwnedSession(sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByAsc(ChatMessage::getCreatedAt));
    }

    /**
     * 分页版消息：按时间倒序取页（第 1 页=最新），前端拿到后倒序展示，
     * 「加载更早」往前翻页。这样无需知道总数也能自然分页。
     */
    public PageResult<ChatMessage> listMessages(Long sessionId, int page, int size) {
        ChatSession session = requireOwnedSession(sessionId);
        Page<ChatMessage> p = messageMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .orderByDesc(ChatMessage::getCreatedAt)
                        // created_at 是秒级 TIMESTAMP，同秒多条消息（流式场景常见）
                        // 必须用 id 兜底，否则翻页会出现重复/缺失消息
                        .orderByDesc(ChatMessage::getId));
        List<ChatMessage> oldestFirst = new ArrayList<>(p.getRecords());
        java.util.Collections.reverse(oldestFirst);
        return PageResult.of(oldestFirst, p.getTotal(), page, size);
    }

    /** 重命名会话：属主校验与删除同源。 */
    public ChatSession renameSession(Long sessionId, String title) {
        ChatSession session = requireOwnedSession(sessionId);
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw AppException.badRequest("会话名称不能为空");
        }
        session.setTitle(trimmed);
        sessionMapper.updateById(session);
        return session;
    }

    public void deleteSession(Long sessionId) {
        ChatSession session = requireOwnedSession(sessionId);
        List<ChatImage> imgs = chatImageMapper.selectList(new LambdaQueryWrapper<ChatImage>()
                .eq(ChatImage::getSessionId, session.getId()));
        for (ChatImage img : imgs) {
            try {
                java.nio.file.Files.deleteIfExists(chatImageService.pathOf(img));
            } catch (Exception ignored) {
            }
        }
        chatImageMapper.delete(new LambdaQueryWrapper<ChatImage>().eq(ChatImage::getSessionId, session.getId()));
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, session.getId()));
        sessionMapper.deleteById(session.getId());
        cacheService.evict(contextKey(session.getId()));
    }

    public SseEmitter ask(Long sessionId, String question, List<Long> imageIds, Long profileId) {
        Long userId = SecurityUtils.currentUserId();
        boolean hasImages = imageIds != null && !imageIds.isEmpty();
        String q = question == null ? "" : question.trim();
        if (q.isEmpty() && !hasImages) {
            throw AppException.badRequest("请输入问题或上传图片");
        }
        if (q.isEmpty()) {
            q = "请看我发的图片，结合健康知识帮我解读。";
        }
        ChatSession session;
        if (sessionId == null) {
            session = createSession(hasImages ? brief("图片问诊 " + q, 24) : brief(q, 24));
        } else {
            session = requireOwnedSession(sessionId);
        }
        if ("新的咨询".equals(session.getTitle())) {
            session.setTitle(hasImages ? "图片问诊" : brief(q, 24));
        }
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(session.getId());
        userMsg.setUserId(userId);
        userMsg.setRole("user");
        userMsg.setContent(q);
        userMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(userMsg);

        List<ChatImage> images = chatImageService.bindToMessage(imageIds, session.getId(), userMsg.getId());
        if (!images.isEmpty()) {
            userMsg.setAttachmentsJson(writeJson(chatImageService.toAttachments(images)));
            messageMapper.updateById(userMsg);
        }

        ChatMessage assistant = new ChatMessage();
        assistant.setSessionId(session.getId());
        assistant.setUserId(userId);
        assistant.setRole("assistant");
        assistant.setContent("");
        assistant.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistant);

        SseEmitter emitter = new SseEmitter(180_000L);
        Long userMsgId = userMsg.getId();
        String questionForModel = q;
        try {
            ssePool.execute(() -> runAsk(emitter, session, assistant, questionForModel, userId, userMsgId, images, profileId));
        } catch (RejectedExecutionException e) {
            throw AppException.tooManyRequests("当前提问人数较多，请稍后再试");
        }
        return emitter;
    }

    private void runAsk(SseEmitter emitter, ChatSession session, ChatMessage assistant,
                        String question, Long userId, Long userMsgId, List<ChatImage> images, Long profileId) {
        try {
            send(emitter, "meta", Map.of("messageId", assistant.getId(), "sessionId", session.getId()));

            String imageContext = chatImageService.describe(images);
            String profileContext = "";
            try {
                profileContext = healthService.briefForChat(profileId);
            } catch (Exception ignored) {
                profileContext = "";
            }
            String extra = joinExtra(imageContext, profileContext);
            List<ChatTurn> history = cachedHistory(session.getId(), assistant.getId(), userMsgId);
            boolean cacheable = history.isEmpty() && (images == null || images.isEmpty()) && profileId == null;
            CachedAnswer cached = cacheable ? loadCache(userId, question) : null;
            // 急症提示走确定性规则，先于模型输出发出去：命中时用户第一眼看到的
            // 就是「打 120」，而不是等模型把一段科普讲完。该提示不进缓存 ——
            // 它只由问题本身决定，每次按当前问题重新判定即可。
            String emergency = EmergencyRules.banner(question);
            if (!emergency.isEmpty()) {
                send(emitter, "delta", Map.of("content", emergency));
            }

            String body;
            if (cached != null) {
                body = cached.content;
                streamCached(emitter, body);
            } else {
                String retrieveQuery = extra.isBlank() ? question : question + "\n" + extra;
                List<ScoredChunk> chunks = ragService.retrieve(retrieveQuery);
                StringBuilder acc = new StringBuilder();
                llmClient.generate(question, chunks, history, extra.isBlank() ? null : extra,
                        chatImageService.toVisionInputs(images), token -> {
                    acc.append(token);
                    try {
                        send(emitter, "delta", Map.of("content", token));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                body = acc.toString();
                if (body.isBlank()) {
                    body = "暂时无法生成回答，请稍后重试。\n\n" + MedicalConstants.DISCLAIMER;
                    send(emitter, "delta", Map.of("content", body));
                }
                List<Citation> citations = ragService.visibleCitations(question, ragService.toCitations(chunks));
                String sourced = withSources(body, citations);
                if (!sourced.equals(body)) {
                    send(emitter, "delta", Map.of("content", sourced.substring(body.length())));
                    body = sourced;
                }
                if (cacheable) {
                    saveCache(userId, question, body);
                }
            }
            String full = emergency + body;

            assistant.setContent(full);
            messageMapper.updateById(assistant);
            saveContext(session.getId(), history, question, full);

            Map<String, Object> done = new HashMap<>();
            done.put("messageId", assistant.getId());
            done.put("fullContent", full);
            send(emitter, "done", done);
            emitter.complete();
        } catch (Exception e) {
            log.warn("问答流失败: {}", e.getMessage());
            String fallback = "生成回答时出现问题，请稍后重试。\n\n" + MedicalConstants.DISCLAIMER;
            try {
                assistant.setContent(fallback);
                messageMapper.updateById(assistant);
                send(emitter, "error", Map.of("message", "生成回答时出现问题，请稍后重试"));
            } catch (Exception ignored) {
            }
            emitter.complete();
        }
    }

    private static String joinExtra(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        if (x.isEmpty()) {
            return y;
        }
        if (y.isEmpty()) {
            return x;
        }
        return x + "\n\n" + y;
    }

    private String withSources(String full, List<Citation> citations) {
        if (full == null || citations == null || citations.isEmpty()) {
            return full == null ? "" : full;
        }
        if (full.contains("**出处**") || full.contains("出处：")) {
            return full;
        }
        List<String> parts = new ArrayList<>();
        for (Citation c : citations) {
            String title = c.getTitle() == null ? "" : c.getTitle().trim();
            String source = c.getSource() == null ? "" : c.getSource().trim();
            if (title.isEmpty()) {
                continue;
            }
            parts.add(source.isEmpty() ? title : title + "（" + source + "）");
        }
        if (parts.isEmpty()) {
            return full;
        }
        String body = full.stripTrailing();
        String disclaimer = MedicalConstants.DISCLAIMER;
        String block = "\n\n**出处** " + String.join("；", parts) + "。";
        int idx = body.lastIndexOf(disclaimer);
        if (idx >= 0) {
            return body.substring(0, idx).stripTrailing() + block + "\n\n" + disclaimer;
        }
        return body + block;
    }

    private void streamCached(SseEmitter emitter, String content) throws IOException {
        int i = 0;
        while (i < content.length()) {
            int end = Math.min(content.length(), i + (Character.isIdeographic(content.charAt(i)) ? 2 : 6));
            send(emitter, "delta", Map.of("content", content.substring(i, end)));
            i = end;
        }
    }

    /** 会话上下文优先读 Redis，未命中再回表并回填，避免每轮问答都查一次库。 */
    private List<ChatTurn> cachedHistory(Long sessionId, Long excludeAssistantId, Long excludeUserId) {
        String raw = cacheService.get(contextKey(sessionId));
        if (raw != null) {
            try {
                return objectMapper.readValue(raw, new TypeReference<List<ChatTurn>>() {
                });
            } catch (JacksonException e) {
                log.warn("会话上下文缓存解析失败，回表重建: {}", e.getMessage());
            }
        }
        List<ChatTurn> history = loadHistory(sessionId, excludeAssistantId, excludeUserId);
        writeContext(sessionId, history);
        return history;
    }

    /** 本轮问答结束后把最新一问一答并入上下文缓存，下一轮直接命中。 */
    private void saveContext(Long sessionId, List<ChatTurn> history, String question, String answer) {
        List<ChatTurn> next = new ArrayList<>(history);
        next.add(new ChatTurn("user", question));
        next.add(new ChatTurn("assistant", answer));
        int keep = Math.max(2, contextTurns);
        if (next.size() > keep) {
            next = new ArrayList<>(next.subList(next.size() - keep, next.size()));
        }
        writeContext(sessionId, next);
    }

    private void writeContext(Long sessionId, List<ChatTurn> turns) {
        try {
            cacheService.set(contextKey(sessionId),
                    objectMapper.writeValueAsString(turns),
                    Duration.ofMinutes(Math.max(1, contextCacheMinutes)));
        } catch (Exception e) {
            log.warn("会话上下文写缓存失败: {}", e.getMessage());
        }
    }

    private static String contextKey(Long sessionId) {
        return "ctx:v1:" + sessionId;
    }

    private List<ChatTurn> loadHistory(Long sessionId, Long excludeAssistantId, Long excludeUserId) {
        List<ChatMessage> msgs = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .notIn(ChatMessage::getId, excludeAssistantId, excludeUserId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT " + Math.max(2, contextTurns)));
        // 附件图片一次 in 查询取代逐条 selectList（带图消息多时是 N+1）
        List<Long> withAttachments = msgs.stream()
                .filter(m -> "user".equalsIgnoreCase(m.getRole()) && m.getAttachmentsJson() != null)
                .map(ChatMessage::getId)
                .toList();
        Map<Long, List<ChatImage>> imagesByMessage = new HashMap<>();
        if (!withAttachments.isEmpty()) {
            for (ChatImage img : chatImageMapper.selectList(new LambdaQueryWrapper<ChatImage>()
                    .in(ChatImage::getMessageId, withAttachments))) {
                imagesByMessage.computeIfAbsent(img.getMessageId(), k -> new ArrayList<>()).add(img);
            }
        }
        List<ChatTurn> turns = new ArrayList<>();
        for (int i = msgs.size() - 1; i >= 0; i--) {
            ChatMessage m = msgs.get(i);
            String content = m.getContent() == null ? "" : m.getContent();
            if ("user".equalsIgnoreCase(m.getRole()) && m.getAttachmentsJson() != null) {
                StringBuilder extra = new StringBuilder();
                for (ChatImage img : imagesByMessage.getOrDefault(m.getId(), List.of())) {
                    if (img.getFilename() != null) {
                        extra.append("\n[附图 ").append(img.getFilename()).append("]");
                    }
                }
                content = content + extra;
            }
            turns.add(new ChatTurn(m.getRole(), content));
        }
        return turns;
    }

    private ChatSession requireOwnedSession(Long id) {
        ChatSession s = sessionMapper.selectById(id);
        if (s == null || !SecurityUtils.currentUserId().equals(s.getUserId())) {
            throw AppException.notFound("会话不存在");
        }
        return s;
    }

    private CachedAnswer loadCache(Long userId, String question) {
        String raw = cacheService.get(cacheKey(userId, question));
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, CachedAnswer.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCache(Long userId, String question, String content) {
        try {
            cacheService.set(cacheKey(userId, question),
                    objectMapper.writeValueAsString(new CachedAnswer(content)),
                    Duration.ofMinutes(cacheTtlMinutes));
        } catch (Exception ignored) {
        }
    }

    private static String cacheKey(Long userId, String question) {
        String norm = question.toLowerCase().replaceAll("\\s+", "");
        return "qa:v6:" + userId + ":" + DigestUtil.md5Hex(norm);
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JacksonException e) {
            return "[]";
        }
    }

    private static String brief(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    public record CachedAnswer(String content) {
    }
}
