package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/health")
@RequiredArgsConstructor
public class AdminHealthController {

    private final HealthService healthService;

    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long userId) {
        return ApiResponse.ok(healthService.adminView(userId));
    }
}
