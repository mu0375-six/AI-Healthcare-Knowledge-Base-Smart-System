package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.dto.HealthDtos;
import com.healthkb.entity.HealthHistory;
import com.healthkb.entity.HealthMetric;
import com.healthkb.entity.HealthProfile;
import com.healthkb.service.HealthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/profiles")
    public ApiResponse<List<HealthProfile>> profiles() {
        return ApiResponse.ok(healthService.listProfiles());
    }

    @PostMapping("/profiles")
    public ApiResponse<HealthProfile> createProfile(@RequestBody HealthDtos.ProfileRequest req) {
        return ApiResponse.ok(healthService.createProfile(req));
    }

    @PutMapping("/profiles/{id}")
    public ApiResponse<HealthProfile> updateOne(@PathVariable Long id, @RequestBody HealthDtos.ProfileRequest req) {
        return ApiResponse.ok(healthService.updateProfile(id, req));
    }

    @DeleteMapping("/profiles/{id}")
    public ApiResponse<Void> deleteOne(@PathVariable Long id) {
        healthService.deleteProfile(id);
        return ApiResponse.ok();
    }

    @GetMapping("/profile")
    public ApiResponse<HealthProfile> profile() {
        return ApiResponse.ok(healthService.getMyProfile());
    }

    @PutMapping("/profile")
    public ApiResponse<HealthProfile> updateProfile(@RequestBody HealthDtos.ProfileRequest req) {
        return ApiResponse.ok(healthService.updateMyProfile(req));
    }

    @GetMapping("/metrics")
    public ApiResponse<List<HealthMetric>> metrics(@RequestParam(value = "profileId", required = false) Long profileId) {
        return ApiResponse.ok(healthService.listMetrics(profileId));
    }

    @PostMapping("/metrics")
    public ApiResponse<HealthMetric> addMetric(@Valid @RequestBody HealthDtos.MetricRequest req) {
        return ApiResponse.ok(healthService.addMetric(req));
    }

    @DeleteMapping("/metrics/{id}")
    public ApiResponse<Void> delMetric(@PathVariable Long id) {
        healthService.deleteMetric(id);
        return ApiResponse.ok();
    }

    @GetMapping("/histories")
    public ApiResponse<List<HealthHistory>> histories(@RequestParam(value = "profileId", required = false) Long profileId) {
        return ApiResponse.ok(healthService.listHistories(profileId));
    }

    @PostMapping("/histories")
    public ApiResponse<HealthHistory> addHistory(@Valid @RequestBody HealthDtos.HistoryRequest req) {
        return ApiResponse.ok(healthService.addHistory(req));
    }

    @DeleteMapping("/histories/{id}")
    public ApiResponse<Void> delHistory(@PathVariable Long id) {
        healthService.deleteHistory(id);
        return ApiResponse.ok();
    }

    @PostMapping("/advice")
    public ApiResponse<Map<String, String>> advice(@RequestParam(value = "profileId", required = false) Long profileId) {
        return ApiResponse.ok(healthService.advice(profileId));
    }
}
