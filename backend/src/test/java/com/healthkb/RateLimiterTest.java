package com.healthkb;

import com.healthkb.common.AppException;
import com.healthkb.common.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试环境没有 Redis，因此这里跑的正是进程内降级实现 ——
 * 恰好是「没有 Docker 也要能挡住刷接口」的那条路径。
 */
@SpringBootTest
class RateLimiterTest {

    @Autowired
    RateLimiter rateLimiter;

    @Test
    void allowsUpToLimitThenRejects() {
        long user = 90001L;
        for (int i = 1; i <= 3; i++) {
            assertTrue(rateLimiter.tryAcquire("t-basic", user, 3, Duration.ofMinutes(5)),
                    "第 " + i + " 次应放行");
        }
        assertFalse(rateLimiter.tryAcquire("t-basic", user, 3, Duration.ofMinutes(5)),
                "第 4 次应被挡下");
    }

    @Test
    void quotaIsPerUserAndPerAction() {
        assertTrue(rateLimiter.tryAcquire("t-iso", 90002L, 1, Duration.ofMinutes(5)));
        assertFalse(rateLimiter.tryAcquire("t-iso", 90002L, 1, Duration.ofMinutes(5)));
        // 换个用户不受影响
        assertTrue(rateLimiter.tryAcquire("t-iso", 90003L, 1, Duration.ofMinutes(5)));
        // 换个动作也不受影响
        assertTrue(rateLimiter.tryAcquire("t-iso-other", 90002L, 1, Duration.ofMinutes(5)));
    }

    @Test
    void nonPositiveLimitDisablesThrottling() {
        for (int i = 0; i < 50; i++) {
            assertTrue(rateLimiter.tryAcquire("t-off", 90004L, 0, Duration.ofMinutes(5)));
        }
    }

    @Test
    void requireThrows429WithGivenMessage() {
        long user = 90005L;
        rateLimiter.require("t-throw", user, 1, Duration.ofMinutes(5), "太快了");
        AppException ex = assertThrows(AppException.class,
                () -> rateLimiter.require("t-throw", user, 1, Duration.ofMinutes(5), "太快了"));
        assertEquals(429, ex.getCode());
        assertEquals("太快了", ex.getMessage());
    }

    @Test
    void windowRollsOverSoQuotaRecovers() throws Exception {
        long user = 90006L;
        Duration oneSecond = Duration.ofSeconds(1);
        assertTrue(rateLimiter.tryAcquire("t-window", user, 1, oneSecond));
        assertFalse(rateLimiter.tryAcquire("t-window", user, 1, oneSecond));
        Thread.sleep(1100);
        assertTrue(rateLimiter.tryAcquire("t-window", user, 1, oneSecond), "窗口滚动后配额应恢复");
    }
}
