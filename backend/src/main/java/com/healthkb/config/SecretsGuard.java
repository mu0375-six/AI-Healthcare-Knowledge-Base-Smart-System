package com.healthkb.config;

import cn.dev33.satoken.config.SaTokenConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * 启动时校验签名密钥是否真的配好了。
 *
 * <p>起因：{@code sa-token.jwt-secret-key: ${APP_JWT_SECRET}} 在环境变量缺失时，
 * Sa-Token 不会报错，而是把字面量 "${APP_JWT_SECRET}" 本身当成 HS256 密钥用 ——
 * 登录照常成功，但这个「密钥」就写在仓库的 application.yml 里，
 * 任何人都能照着伪造出任意用户的 token。属于静默失败，必须在启动时挡掉。
 */
@Configuration
public class SecretsGuard {

    private final SaTokenConfig saTokenConfig;

    public SecretsGuard(SaTokenConfig saTokenConfig) {
        this.saTokenConfig = saTokenConfig;
    }

    @PostConstruct
    void verifyJwtSecret() {
        String secret = saTokenConfig.getJwtSecretKey();
        if (secret == null || secret.isBlank() || secret.contains("${")) {
            throw new IllegalStateException("""
                    登录令牌签名密钥未配置（sa-token.jwt-secret-key 当前值无效）。
                    请在项目根目录准备 .env：copy .env.example .env，
                    再把 APP_JWT_SECRET 填成一段足够长的随机串后重启。""");
        }
    }
}
