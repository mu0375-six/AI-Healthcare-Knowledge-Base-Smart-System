package com.healthkb.security;

import com.healthkb.entity.SysUser;

/**
 * 当前登录用户的轻量视图（由 Sa-Token 登录态 + 数据库补充得到）。
 */
public record AuthUser(Long userId, String username, String role) {

    public static AuthUser from(SysUser u) {
        return new AuthUser(u.getId(), u.getUsername(), u.getRole());
    }
}
