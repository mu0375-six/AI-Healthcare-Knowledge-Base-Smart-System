package com.healthkb.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取发起请求的客户端 IP。优先 X-Forwarded-For 的第一个地址
 * （经过 nginx 反代后 remoteAddr 是代理自身），没有再回退 remoteAddr。
 * 只用于登录限流的键，取值宁可保守也不做复杂解析。
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
