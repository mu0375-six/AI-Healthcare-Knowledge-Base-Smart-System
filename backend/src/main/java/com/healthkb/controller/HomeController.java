package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.dto.HomeDtos;
import com.healthkb.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/overview")
    public ApiResponse<HomeDtos.Overview> overview() {
        return ApiResponse.ok(homeService.overview());
    }
}
