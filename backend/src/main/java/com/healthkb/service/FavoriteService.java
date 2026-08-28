package com.healthkb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.healthkb.common.AppException;
import com.healthkb.dto.PageResult;
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
        return list(1, Integer.MAX_VALUE).records();
    }

    /** 分页版：收藏攒多后列表页不再全量渲染。 */
    public PageResult<Map<String, Object>> list(int page, int size) {
        Long userId = SecurityUtils.currentUserId();
        Page<Favorite> p = favoriteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreatedAt)
                        // 同秒收藏并列时以 id 兜底，保证翻页顺序稳定
                        .orderByDesc(Favorite::getId));
        List<Favorite> favs = p.getRecords();
        if (favs.isEmpty()) {
            return PageResult.of(List.of(), p.getTotal(), page, size);
        }
        // 一次批查取代逐条 selectById（N+1 读在收藏多时拖慢列表页）
        List<Long> messageIds = favs.stream().map(Favorite::getMessageId).toList();
        Map<Long, ChatMessage> messages = new HashMap<>();
        for (ChatMessage msg : messageMapper.selectByIds(messageIds)) {
            messages.put(msg.getId(), msg);
        }
        List<Map<String, Object>> out = favs.stream().map(f -> {
            ChatMessage msg = messages.get(f.getMessageId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("messageId", f.getMessageId());
            m.put("createdAt", f.getCreatedAt());
            m.put("content", msg == null ? "" : msg.getContent());
            m.put("sessionId", msg == null ? null : msg.getSessionId());
            return m;
        }).toList();
        return PageResult.of(out, p.getTotal(), page, size);
    }
}
