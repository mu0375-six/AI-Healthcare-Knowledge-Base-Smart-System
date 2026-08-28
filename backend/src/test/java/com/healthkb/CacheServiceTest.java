package com.healthkb;

import com.healthkb.cache.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 测试环境没有 Redis，跑的正是内存降级实现 ——
 * 与 RateLimiterTest 同理，覆盖的恰是「没有 Docker 也要能用」的那条路径。
 */
@SpringBootTest
class CacheServiceTest {

    @Autowired
    CacheService cacheService;

    @Test
    void setGetRoundtrip() {
        cacheService.set("t:round", "hello", Duration.ofMinutes(5));
        assertEquals("hello", cacheService.get("t:round"));
        assertNull(cacheService.get("t:missing"));
    }

    @Test
    void valueExpiresAfterTtl() throws Exception {
        cacheService.set("t:ttl", "gone soon", Duration.ofMillis(150));
        assertEquals("gone soon", cacheService.get("t:ttl"));
        Thread.sleep(250);
        assertNull(cacheService.get("t:ttl"), "过期后应读不到");
    }

    @Test
    void evictRemovesImmediately() {
        cacheService.set("t:evict", "x", Duration.ofMinutes(5));
        cacheService.evict("t:evict");
        assertNull(cacheService.get("t:evict"));
    }

    @Test
    void overwriteReplacesValue() {
        cacheService.set("t:overwrite", "v1", Duration.ofMinutes(5));
        cacheService.set("t:overwrite", "v2", Duration.ofMinutes(5));
        assertEquals("v2", cacheService.get("t:overwrite"));
    }
}
