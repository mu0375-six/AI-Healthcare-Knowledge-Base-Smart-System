package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.common.RateLimiter;
import com.healthkb.security.SecurityUtils;
import com.healthkb.dto.HealthDtos;
import com.healthkb.entity.HealthHistory;
import com.healthkb.entity.HealthMetric;
import com.healthkb.entity.HealthProfile;
import com.healthkb.service.HealthService;
import com.healthkb.service.MetricTrendAnalyzer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;
    private final RateLimiter rateLimiter;

    @Value("${app.rate-limit.advice.limit:10}")
    private int adviceLimit;

    @Value("${app.rate-limit.advice.window-seconds:60}")
    private int adviceWindowSeconds;

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

    /** CSV 等批量导入，单次上限 500 条（DTO 校验）。 */
    @PostMapping("/metrics/batch")
    public ApiResponse<Integer> addMetrics(@Valid @RequestBody HealthDtos.MetricBatchRequest req) {
        return ApiResponse.ok(healthService.addMetrics(req));
    }

    /** 指标参考区间唯一权威源下发；前端不再自持一份阈值。 */
    @GetMapping("/reference")
    public ApiResponse<List<Map<String, Object>>> reference() {
        return ApiResponse.ok(healthService.reference());
    }

    /** 全档案异常提醒：连续超标 + 待观察两级。 */
    @GetMapping("/alerts")
    public ApiResponse<List<HealthDtos.AlertItem>> alerts() {
        return ApiResponse.ok(healthService.alerts());
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

    @GetMapping("/trends")
    public ApiResponse<List<MetricTrendAnalyzer.Trend>> trends(
            @RequestParam(value = "profileId", required = false) Long profileId) {
        return ApiResponse.ok(healthService.trends(profileId));
    }

    @PostMapping("/advice")
    public ApiResponse<Map<String, String>> advice(@RequestParam(value = "profileId", required = false) Long profileId) {
        rateLimiter.require("health-advice", SecurityUtils.currentUserId(), adviceLimit,
                Duration.ofSeconds(adviceWindowSeconds), "生成建议太频繁了，请稍后再试");
        return ApiResponse.ok(healthService.advice(profileId));
    }
}
