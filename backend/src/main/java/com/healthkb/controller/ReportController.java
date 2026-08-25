package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.entity.ExamReport;
import com.healthkb.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "extractedText", required = false) String extractedText,
            @RequestParam(value = "profileId", required = false) Long profileId) {
        return ApiResponse.ok(reportService.upload(file, extractedText, profileId));
    }

    @PostMapping("/{id}/import")
    public ApiResponse<Map<String, Object>> importToProfile(
            @PathVariable Long id,
            @RequestParam("profileId") Long profileId) {
        return ApiResponse.ok(reportService.importToProfile(id, profileId));
    }

    @GetMapping
    public ApiResponse<List<ExamReport>> list() {
        return ApiResponse.ok(reportService.listMine());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(reportService.detail(id));
    }
}
