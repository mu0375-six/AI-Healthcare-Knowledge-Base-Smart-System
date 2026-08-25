package com.healthkb;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * sa-token-redis-template 把 Redis DAO 无条件写进 AutoConfiguration.imports，
 * Redis 连不上时鉴权会整体 500（实测过）。SaTokenDaoConfig 用 @Primary 抢下这个注入点，
 * 探测失败就退回内存实现。
 *
 * <p>测试环境的 spring.data.redis 指向端口 1，必然连不上，正好覆盖降级分支。
 * 这里把行为钉住：谁把 @Primary 摘了或改了判定顺序，这条会红。
 */
@SpringBootTest
class SaTokenDaoFallbackTest {

    @Autowired
    SaTokenDao saTokenDao;

    @Test
    void redisUnreachableFallsBackToMemoryDao() {
        assertInstanceOf(SaTokenDaoDefaultImpl.class, saTokenDao,
                "Redis 不可用时应退回内存 DAO，实际: " + saTokenDao.getClass().getName());
    }

    @Test
    void saTokenActuallyUsesTheFallbackDao() {
        // 光有 Bean 不够 —— 必须确认 Sa-Token 内部拿到的就是这个实例，
        // 否则自动配置那个 Redis DAO 仍会在运行时被调用。
        assertSame(saTokenDao, StpUtil.stpLogic.getSaTokenDao(),
                "Sa-Token 实际使用的 DAO 与容器里 @Primary 的不是同一个");
    }
}
