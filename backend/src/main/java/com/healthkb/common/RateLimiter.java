package com.healthkb.common;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 按用户维度的固定窗口限流。
 *
 * <p>动机：问答、报告解读、健康建议三个接口都会直连大模型，是本项目里
 * 唯一直接产生费用的地方。在此之前没有任何配额约束 ——
 * 一个拿到 token 的循环脚本就能把 API 额度刷光。
 *
 * <p>降级策略与 {@code CacheService} 保持一致：优先用 Redis 计数（多实例共享），
 * Redis 不可用时退回进程内计数。退回后各实例配额独立，总量会放大到实例数倍，
 * 但对「防脚本刷爆」这个目标仍然有效，比完全不限强。
 */
@Slf4j
@Service
public class RateLimiter {

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private volatile StringRedisTemplate redis;

    private final ConcurrentHashMap<String, Window> memory = new ConcurrentHashMap<>();

    public RateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    @PostConstruct
    public void init() {
        StringRedisTemplate candidate = redisProvider.getIfAvailable();
        if (candidate == null) {
            log.warn("未装配 StringRedisTemplate，限流使用进程内计数");
            return;
        }
        try {
            candidate.getConnectionFactory().getConnection().ping();
            redis = candidate;
            log.info("Redis 已连接，限流计数跨实例共享");
        } catch (Exception e) {
            log.warn("Redis 不可用，限流改用进程内计数: {}", e.toString());
        }
    }

    /**
     * 消耗一次配额。
     *
     * @param action 动作名，用于区分不同接口的独立配额
     * @param userId 用户 id
     * @return 放行返回 true，超限返回 false
     */
    public boolean tryAcquire(String action, Long userId, int limit, Duration window) {
        if (limit <= 0) {
            return true; // 配额 <=0 视为不限流
        }
        long windowSeconds = Math.max(1, window.toSeconds());
        long slot = Instant.now().getEpochSecond() / windowSeconds;
        String key = "rl:" + action + ":" + userId + ":" + slot;

        StringRedisTemplate current = redis;
        if (current != null) {
            try {
                Long count = current.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    // 首次计数才设过期，避免每次请求都刷新窗口
                    current.expire(key, Duration.ofSeconds(windowSeconds + 1));
                }
                return count == null || count <= limit;
            } catch (Exception e) {
                log.warn("Redis 限流计数失败，本次改用进程内计数: {}", e.toString());
            }
        }
        return memoryAcquire(key, limit, windowSeconds);
    }

    /** 超限直接抛 429，供 Controller 直接调用。 */
    public void require(String action, Long userId, int limit, Duration window, String message) {
        if (!tryAcquire(action, userId, limit, window)) {
            throw AppException.tooManyRequests(message);
        }
    }

    private boolean memoryAcquire(String key, int limit, long windowSeconds) {
        Instant expireAt = Instant.now().plusSeconds(windowSeconds + 1);
        Window w = memory.compute(key, (k, old) ->
                old != null && old.expireAt.isAfter(Instant.now()) ? old : new Window(expireAt));
        long count = w.count.incrementAndGet();
        if (memory.size() > 10_000) {
            evictExpired();
        }
        return count <= limit;
    }

    private void evictExpired() {
        Instant now = Instant.now();
        memory.entrySet().removeIf(e -> e.getValue().expireAt.isBefore(now));
    }

    private static final class Window {
        final AtomicLong count = new AtomicLong();
        final Instant expireAt;

        Window(Instant expireAt) {
            this.expireAt = expireAt;
        }
    }
}
