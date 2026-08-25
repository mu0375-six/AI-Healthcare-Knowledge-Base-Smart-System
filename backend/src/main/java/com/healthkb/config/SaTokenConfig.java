package com.healthkb.config;

import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.healthkb.mapper.SysUserMapper;
import com.healthkb.security.SecurityUtils;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.EnumSet;

/**
 * Sa-Token + JWT 登录校验配置，替代原 Spring Security SecurityFilterChain。
 * 路由规则：
 *   - /api/auth/login、/api/auth/register 放行
 *   - /api/admin/** 需登录且角色为 ADMIN
 *   - 其余 /api/** 需登录
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private final SysUserMapper userMapper;

    public SaTokenConfig(SysUserMapper userMapper) {
        this.userMapper = userMapper;
        // 让 SecurityUtils 能访问数据库补齐当前用户信息
        SecurityUtils.init(userMapper);
    }

    /**
     * Sa-Token 的上下文过滤器是普通 Filter，Spring Boot 默认只注册 DispatcherType.REQUEST，
     * 于是 SSE 问答（SseEmitter）完成后的 ASYNC 再分发上下文为空，
     * SaInterceptor 会抛 SaTokenContextException。这里显式注册全部 DispatcherType。
     */
    @Bean
    public FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> saTokenContextFilterRegistration(
            SaTokenContextFilterForJakartaServlet filter) {
        FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> registration =
                new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/api/**")
                    .notMatch("/api/auth/login", "/api/auth/register", "/error")
                    .check(r -> StpUtil.checkLogin());
            SaRouter.match("/api/admin/**")
                    .check(r -> StpUtil.checkRole("ADMIN"));
        })).addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
