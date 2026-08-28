package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.common.RateLimiter;
import com.healthkb.security.SecurityUtils;
import com.healthkb.dto.ChatDtos;
import com.healthkb.dto.PageResult;
import com.healthkb.entity.ChatMessage;
import com.healthkb.entity.ChatSession;
import com.healthkb.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import com.healthkb.entity.ChatImage;
import com.healthkb.service.ChatImageService;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatImageService chatImageService;
    private final RateLimiter rateLimiter;

    @Value("${app.rate-limit.chat.limit:20}")
    private int chatLimit;

    @Value("${app.rate-limit.chat.window-seconds:60}")
    private int chatWindowSeconds;

    @PostMapping("/chat/sessions")
    public ApiResponse<ChatSession> create(@RequestBody(required = false) ChatDtos.CreateSessionRequest req) {
        String title = req == null ? null : req.getTitle();
        return ApiResponse.ok(chatService.createSession(title));
    }

    @GetMapping("/chat/sessions")
    public ApiResponse<PageResult<ChatSession>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.clampSize(size, 50);
        return ApiResponse.ok(chatService.listSessions(p, s));
    }

    @GetMapping("/chat/sessions/{id}/messages")
    public ApiResponse<PageResult<ChatMessage>> messages(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int p = PageResult.normalizePage(page);
        int s = PageResult.clampSize(size, 50);
        return ApiResponse.ok(chatService.listMessages(id, p, s));
    }

    @org.springframework.web.bind.annotation.PutMapping("/chat/sessions/{id}")
    public ApiResponse<ChatSession> rename(@PathVariable Long id,
                                           @jakarta.validation.Valid @RequestBody ChatDtos.RenameSessionRequest req) {
        return ApiResponse.ok(chatService.renameSession(id, req.getTitle()));
    }

    @DeleteMapping("/chat/sessions/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        chatService.deleteSession(id);
        return ApiResponse.ok();
    }

    @PostMapping("/chat/images")
    public ApiResponse<Map<String, Object>> uploadImage(@RequestPart("file") MultipartFile file) {
        ChatImage img = chatImageService.upload(file);
        return ApiResponse.ok(Map.of(
                "id", img.getId(),
                "filename", img.getFilename() == null ? "image" : img.getFilename(),
                "mimeType", img.getMimeType() == null ? "image/jpeg" : img.getMimeType()
        ));
    }

    @GetMapping("/chat/images/{id}")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        ChatImage img = chatImageService.requireOwned(id);
        var path = chatImageService.pathOf(img);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        MediaType media = MediaType.parseMediaType(img.getMimeType() == null ? "image/jpeg" : img.getMimeType());
        String name = safeFilename(img.getFilename() == null ? "image.jpg" : img.getFilename());
        return ResponseEntity.ok()
                .contentType(media)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .body(new FileSystemResource(path));
    }

    /** 头部值里不允许 CR/LF 与控制字符：不是注入也是 500，一律剥掉。 */
    private static String safeFilename(String raw) {
        String cleaned = raw.replace("\"", "").replaceAll("[\\x00-\\x1f\\x7f]", "").trim();
        return cleaned.isEmpty() ? "image.jpg" : cleaned;
    }

    @PostMapping(value = "/chat/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@jakarta.validation.Valid @RequestBody ChatDtos.AskRequest req,
                          HttpServletResponse response) {
        // 问答直连大模型，是最主要的花钱口子，先扣配额再进业务
        rateLimiter.require("chat", SecurityUtils.currentUserId(), chatLimit,
                Duration.ofSeconds(chatWindowSeconds), "提问太频繁了，请稍后再试");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return chatService.ask(req.getSessionId(), req.getQuestion(), req.getImageIds(), req.getProfileId());
    }
}
