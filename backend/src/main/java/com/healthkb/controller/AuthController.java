package com.healthkb.controller;

import com.healthkb.common.ApiResponse;
import com.healthkb.common.ClientIp;
import com.healthkb.common.RateLimiter;
import com.healthkb.dto.AuthDtos;
import com.healthkb.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    /**
     * 登录与注册是仅有的两个匿名写接口，也是密码爆破的入口，按 IP 限流。
     * 键里带动作名，登录与注册各自独立计数；设为 0 表示不限流（本地压测用）。
     */
    @Value("${app.rate-limit.auth.limit:10}")
    private int authLimit;

    @Value("${app.rate-limit.auth.window-seconds:60}")
    private long authWindowSeconds;

    @PostMapping("/register")
    public ApiResponse<AuthDtos.UserView> register(@Valid @RequestBody AuthDtos.RegisterRequest req,
                                                   HttpServletRequest request) {
        rateLimiter.require("auth-register", ClientIp.of(request), authLimit,
                Duration.ofSeconds(authWindowSeconds), "尝试过于频繁，请稍后再试");
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req,
                                                     HttpServletRequest request) {
        rateLimiter.require("auth-login", ClientIp.of(request), authLimit,
                Duration.ofSeconds(authWindowSeconds), "尝试过于频繁，请稍后再试");
        return ApiResponse.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserView> me() {
        return ApiResponse.ok(authService.me());
    }

    /** 改密码与登录同源（猜旧密码=爆破），共用同一档 IP 限流；成功后当前会话注销。 */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest req,
                                            HttpServletRequest request) {
        rateLimiter.require("auth-password", ClientIp.of(request), authLimit,
                Duration.ofSeconds(authWindowSeconds), "尝试过于频繁，请稍后再试");
        authService.changePassword(req);
        return ApiResponse.ok();
    }
}
