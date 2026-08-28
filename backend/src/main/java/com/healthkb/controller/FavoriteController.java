package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.dto.PageResult;
import com.healthkb.entity.Favorite;
import com.healthkb.service.FavoriteService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public ApiResponse<Favorite> add(@RequestBody FavReq req) {
        return ApiResponse.ok(favoriteService.add(req.getMessageId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        favoriteService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(favoriteService.list(
                PageResult.normalizePage(page), PageResult.clampSize(size, 20)));
    }

    @Data
    public static class FavReq {
        private Long messageId;
    }
}
