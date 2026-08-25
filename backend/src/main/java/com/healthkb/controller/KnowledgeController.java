package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.dto.KnowledgeDtos;
import com.healthkb.entity.KbDocument;
import com.healthkb.service.KnowledgeService;
import com.healthkb.service.OfficialKnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final OfficialKnowledgeService officialKnowledgeService;

    @GetMapping("/admin/knowledge")
    public ApiResponse<List<KbDocument>> list() {
        return ApiResponse.ok(knowledgeService.listDocuments());
    }

    @PostMapping("/admin/knowledge/upload")
    public ApiResponse<KbDocument> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "extractedText", required = false) String extractedText) {
        return ApiResponse.ok(knowledgeService.upload(file, title, category, source, extractedText));
    }

    @PostMapping("/admin/knowledge/text")
    public ApiResponse<KbDocument> addText(@Valid @RequestBody KnowledgeDtos.TextRequest req) {
        return ApiResponse.ok(knowledgeService.addText(req));
    }

    @PostMapping("/admin/knowledge/sync-official")
    public ApiResponse<OfficialKnowledgeService.SyncResult> syncOfficial(
            @RequestParam(value = "replaceDemo", defaultValue = "true") boolean replaceDemo) {
        return ApiResponse.ok(officialKnowledgeService.sync(replaceDemo));
    }

    @DeleteMapping("/admin/knowledge/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/knowledge/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam("q") String q) {
        return ApiResponse.ok(knowledgeService.search(q));
    }

    @GetMapping("/knowledge/vectors/status")
    public ApiResponse<com.healthkb.rag.VectorStoreInfo> vectorStatus() {
        return ApiResponse.ok(knowledgeService.vectorStatus());
    }

    @GetMapping("/knowledge/vectors/inspect")
    public ApiResponse<Map<String, Object>> inspect(@RequestParam("q") String q) {
        return ApiResponse.ok(knowledgeService.vectorInspect(q));
    }

    @PostMapping("/admin/knowledge/reindex")
    public ApiResponse<Map<String, Object>> reindex() {
        int n = knowledgeService.reindex();
        return ApiResponse.ok(Map.of("count", n, "store", knowledgeService.vectorStatus()));
    }

    @GetMapping("/knowledge/terms")
    public ApiResponse<List<String>> terms() {
        return ApiResponse.ok(knowledgeService.terms());
    }
}
