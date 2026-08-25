package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.dto.TriageDtos;
import com.healthkb.service.TriageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/triage")
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;

    @PostMapping
    public ApiResponse<TriageDtos.Response> triage(@Valid @RequestBody TriageDtos.Request req) {
        return ApiResponse.ok(triageService.triage(req));
    }
}
