package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.dto.ChatDtos;
import com.healthkb.entity.ChatMessage;
import com.healthkb.entity.ChatSession;
import com.healthkb.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatImageService chatImageService;

    @PostMapping("/chat/sessions")
    public ApiResponse<ChatSession> create(@RequestBody(required = false) ChatDtos.CreateSessionRequest req) {
        String title = req == null ? null : req.getTitle();
        return ApiResponse.ok(chatService.createSession(title));
    }

    @GetMapping("/chat/sessions")
    public ApiResponse<List<ChatSession>> list() {
        return ApiResponse.ok(chatService.listSessions());
    }

    @GetMapping("/chat/sessions/{id}/messages")
    public ApiResponse<List<ChatMessage>> messages(@PathVariable Long id) {
        return ApiResponse.ok(chatService.listMessages(id));
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
        String name = img.getFilename() == null ? "image.jpg" : img.getFilename().replace("\"", "");
        return ResponseEntity.ok()
                .contentType(media)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + name + "\"")
                .body(new FileSystemResource(path));
    }

    @PostMapping(value = "/chat/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody ChatDtos.AskRequest req, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return chatService.ask(req.getSessionId(), req.getQuestion(), req.getImageIds(), req.getProfileId());
    }
}
