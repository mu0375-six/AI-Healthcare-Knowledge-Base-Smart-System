package com.healthkb.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.common.ApiResponse;
import com.healthkb.common.AppException;
import com.healthkb.dto.NewsDtos;
import com.healthkb.entity.NewsItem;
import com.healthkb.mapper.NewsItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NewsController {

    private final NewsItemMapper newsMapper;

    @Value("${app.news.image-dir:./data/news-images}")
    private String imageDir;

    @GetMapping("/news")
    public ApiResponse<List<NewsDtos.ListItem>> list(@RequestParam(defaultValue = "12") int limit) {
        int size = Math.max(1, Math.min(limit, 30));
        List<NewsItem> items = newsMapper.selectList(new LambdaQueryWrapper<NewsItem>()
                .orderByDesc(NewsItem::getPublishedOn)
                .orderByDesc(NewsItem::getId)
                .last("LIMIT " + size));
        return ApiResponse.ok(items.stream().map(this::toList).toList());
    }

    @GetMapping("/news/{id}")
    public ApiResponse<NewsDtos.Detail> detail(@PathVariable Long id) {
        NewsItem n = newsMapper.selectById(id);
        if (n == null) {
            throw AppException.notFound("新闻不存在或已下线");
        }
        NewsDtos.Detail d = new NewsDtos.Detail();
        d.setId(n.getId());
        d.setTitle(n.getTitle());
        d.setSummary(n.getSummary());
        d.setContent(n.getContent());
        d.setSourceName(n.getSourceName());
        d.setSourceUrl(n.getSourceUrl());
        d.setCategory(n.getCategory());
        d.setPublishedOn(n.getPublishedOn() == null ? null : n.getPublishedOn().toString());
        d.setImage(hasImage(n));
        return ApiResponse.ok(d);
    }

    /** 配图走鉴权接口由前端取 blob 展示（与聊天图片同一套路数），不热链外站。 */
    @GetMapping("/news/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        NewsItem n = newsMapper.selectById(id);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        if (n.getImageName() != null && !n.getImageName().isBlank()) {
            Path path = Path.of(imageDir, n.getImageName());
            if (Files.isRegularFile(path)) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                        .body(new FileSystemResource(path));
            }
        }
        if (n.getBuiltinImage() != null && !n.getBuiltinImage().isBlank()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/svg+xml"))
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                    .body(new ClassPathResource("news-images/" + n.getBuiltinImage() + ".svg"));
        }
        return ResponseEntity.notFound().build();
    }

    private NewsDtos.ListItem toList(NewsItem n) {
        NewsDtos.ListItem i = new NewsDtos.ListItem();
        i.setId(n.getId());
        i.setTitle(n.getTitle());
        i.setSummary(n.getSummary());
        i.setSourceName(n.getSourceName());
        i.setCategory(n.getCategory());
        i.setPublishedOn(n.getPublishedOn() == null ? null : n.getPublishedOn().toString());
        i.setImage(hasImage(n));
        return i;
    }

    private static boolean hasImage(NewsItem n) {
        return (n.getImageName() != null && !n.getImageName().isBlank())
                || (n.getBuiltinImage() != null && !n.getBuiltinImage().isBlank());
    }
}
