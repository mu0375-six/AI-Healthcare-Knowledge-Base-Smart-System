package com.healthkb.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Sa-Token 会话存储。
 *
 * <p>默认落 Redis：JWT 是 Simple 风格，token 里只带 loginId，登录态本身仍在 SaSession 里，
 * 存进程内存的话后端一重启所有人被登出。接上 Redis DAO 后 token 可以跨重启继续用。
 *
 * <p>为什么要自己注册这个 Bean：{@code sa-token-redis-template} 把
 * {@link SaTokenDaoForRedisTemplate} 直接写进 AutoConfiguration.imports，且类上没有任何
 * {@code @Conditional}——只要 jar 在就无条件生效。那样 Redis 一停，鉴权整个瘫痪，
 * 与本项目其余部分（缓存、向量库、embedding）"连不上就降级"的做法不一致，
 * 也会让"没有 Docker 也能演示"不再成立。这里启动时探一次连接，连不上就退回内存实现。
 *
 * <p>本 Bean 标 {@link Primary}，注入点优先选它；自动配置那个 Bean 仍会存在但不会被使用。
 *
 * <p>局限：只在启动时判定一次。运行中途 Redis 掉线不会自动切回内存——
 * 那种情况下鉴权会失败，与缓存层的行为不同（缓存每次调用都能降级）。
 */
@Slf4j
@Configuration
public class SaTokenDaoConfig {

    @Bean
    @Primary
    public SaTokenDao saTokenDao(ObjectProvider<RedisConnectionFactory> factoryProvider) {
        RedisConnectionFactory factory = factoryProvider.getIfAvailable();
        if (factory == null) {
            log.warn("未装配 RedisConnectionFactory，Sa-Token 会话使用内存存储：后端重启后需重新登录");
            return new SaTokenDaoDefaultImpl();
        }
        try {
            factory.getConnection().ping();
        } catch (Exception e) {
            log.warn("Redis 不可用，Sa-Token 会话改用内存存储（后端重启后需重新登录）: {}", e.toString());
            return new SaTokenDaoDefaultImpl();
        }
        SaTokenDaoForRedisTemplate dao = new SaTokenDaoForRedisTemplate();
        dao.init(factory);
        log.info("Sa-Token 会话已接入 Redis，登录态可跨后端重启保留");
        return dao;
    }
}
