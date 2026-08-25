package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.common.AppException;
import com.healthkb.entity.ChatMessage;
import com.healthkb.entity.Favorite;
import com.healthkb.mapper.ChatMessageMapper;
import com.healthkb.mapper.FavoriteMapper;
import com.healthkb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ChatMessageMapper messageMapper;

    public Favorite add(Long messageId) {
        Long userId = SecurityUtils.currentUserId();
        ChatMessage msg = messageMapper.selectById(messageId);
        if (msg == null || !userId.equals(msg.getUserId())) {
            throw AppException.notFound("消息不存在");
        }
        Favorite exists = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getMessageId, messageId));
        if (exists != null) {
            return exists;
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setMessageId(messageId);
        fav.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(fav);
        return fav;
    }

    public void delete(Long id) {
        Long userId = SecurityUtils.currentUserId();
        Favorite fav = favoriteMapper.selectById(id);
        if (fav == null || !userId.equals(fav.getUserId())) {
            throw AppException.notFound("收藏不存在");
        }
        favoriteMapper.deleteById(id);
    }

    public List<Map<String, Object>> list() {
        Long userId = SecurityUtils.currentUserId();
        List<Favorite> favs = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreatedAt));
        return favs.stream().map(f -> {
            ChatMessage msg = messageMapper.selectById(f.getMessageId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("messageId", f.getMessageId());
            m.put("createdAt", f.getCreatedAt());
            m.put("content", msg == null ? "" : msg.getContent());
            m.put("citationsJson", msg == null ? null : msg.getCitationsJson());
            m.put("sessionId", msg == null ? null : msg.getSessionId());
            return m;
        }).toList();
    }
}
