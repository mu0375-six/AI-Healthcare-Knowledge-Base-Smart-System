package com.healthkb.config;

import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 开启 Sa-Token 的 JWT 模式（Simple 风格：JWT 承载 loginId，同时保留 Session 能力）。
 *
 * <p>注意：仅在 application.yml 里写 {@code token-style: jwt} 是无效的 ——
 * Sa-Token 的 token-style 只认 uuid / simple-uuid / random-xx / tik，
 * 未识别的值会落到 default 分支退化成 UUID。必须注册本 Bean，
 * 登录后 {@code StpUtil.getTokenValue()} 才是真正的三段式 JWT。
 * 签名密钥取 {@code sa-token.jwt-secret-key}。
 */
@Configuration
public class StpLogicJwtConfig {

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }
}
