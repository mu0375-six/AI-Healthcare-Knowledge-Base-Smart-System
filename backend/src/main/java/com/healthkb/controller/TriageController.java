package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.dto.TriageDtos;
import com.healthkb.security.SecurityUtils;
import com.healthkb.service.NearbyService;
import com.healthkb.service.TriageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/triage")
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;
    private final NearbyService nearbyService;

    @PostMapping
    public ApiResponse<TriageDtos.Response> triage(@Valid @RequestBody TriageDtos.Request req) {
        return ApiResponse.ok(triageService.triage(req));
    }

    /** 附近医疗资源：真实 POI 来自高德，LLM 只做解释。位置默认不落库，勾选「保存」才存。 */
    @PostMapping("/nearby")
    public ApiResponse<TriageDtos.NearbyResponse> nearby(@RequestBody TriageDtos.NearbyRequest req) {
        return ApiResponse.ok(nearbyService.nearby(req, SecurityUtils.currentUserId()));
    }

    @GetMapping("/location")
    public ApiResponse<TriageDtos.SavedLocation> savedLocation() {
        return ApiResponse.ok(nearbyService.savedLocation(SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/location")
    public ApiResponse<Void> clearLocation() {
        nearbyService.clearLocation(SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }
}
