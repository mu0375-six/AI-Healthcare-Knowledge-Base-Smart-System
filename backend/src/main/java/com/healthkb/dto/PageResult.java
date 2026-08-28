package com.healthkb.dto;

import java.util.List;

/**
 * 统一分页返回结构。会话、消息、收藏、知识库列表共用，
 * 前端按 total 判断「加载更多 / 分页条」是否出现。
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }

    /** 钳制页大小：默认值之外也防恶意传大值一次性拖全表。 */
    public static int clampSize(Integer size, int defaultSize) {
        if (size == null || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, 100);
    }

    public static int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }
}
