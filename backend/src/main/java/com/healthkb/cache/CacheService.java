package com.healthkb.cache;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 问答缓存与会话上下文缓存。
 *
 * <p>基于 spring-boot-starter-data-redis 的 {@link StringRedisTemplate}；
 * Redis 不可用（未启动 / 连接中断）时自动退回进程内 Map，保证演示环境不依赖 Redis。
 */
@Slf4j
@Service
public class CacheService {

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private volatile StringRedisTemplate redis;

    private final ConcurrentHashMap<String, Entry> memory = new ConcurrentHashMap<>();

    public CacheService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
    }

    @PostConstruct
    public void init() {
        StringRedisTemplate candidate = redisProvider.getIfAvailable();
        if (candidate == null) {
            log.warn("未装配 StringRedisTemplate，缓存使用内存实现");
            return;
        }
        try {
            candidate.getConnectionFactory().getConnection().ping();
            redis = candidate;
            log.info("Redis 已连接，问答与会话上下文缓存生效");
        } catch (Exception e) {
            log.warn("Redis 不可用，缓存改用内存实现: {}", e.toString());
        }
    }

    public String get(String key) {
        evictExpired();
        StringRedisTemplate current = redis;
        if (current != null) {
            try {
                return current.opsForValue().get(key);
            } catch (Exception e) {
                degrade("读取", e);
            }
        }
        Entry e = memory.get(key);
        if (e == null || e.expireAt.isBefore(Instant.now())) {
            memory.remove(key);
            return null;
        }
        return e.value;
    }

    public void set(String key, String value, Duration ttl) {
        StringRedisTemplate current = redis;
        if (current != null) {
            try {
                current.opsForValue().set(key, value, Duration.ofSeconds(Math.max(1, ttl.toSeconds())));
                return;
            } catch (Exception e) {
                degrade("写入", e);
            }
        }
        memory.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    public void evict(String key) {
        memory.remove(key);
        StringRedisTemplate current = redis;
        if (current != null) {
            try {
                current.delete(key);
            } catch (Exception e) {
                degrade("删除", e);
            }
        }
    }

    private void degrade(String op, Exception e) {
        redis = null;
        log.warn("Redis {}失败，切换内存缓存: {}", op, e.toString());
    }

    private void evictExpired() {
        Instant now = Instant.now();
        memory.entrySet().removeIf(e -> e.getValue().expireAt.isBefore(now));
    }

    private record Entry(String value, Instant expireAt) {
    }
}
