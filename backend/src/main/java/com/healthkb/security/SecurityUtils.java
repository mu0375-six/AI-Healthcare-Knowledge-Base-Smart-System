package com.healthkb.security;

import com.healthkb.common.AppException;
import com.healthkb.entity.SysUser;
import com.healthkb.mapper.SysUserMapper;
import cn.dev33.satoken.stp.StpUtil;

public final class SecurityUtils {
    private static volatile SysUserMapper userMapper;

    private SecurityUtils() {
    }

    /** 供 Spring 上下文初始化时注入，用于从数据库补齐当前用户信息。 */
    public static void init(SysUserMapper mapper) {
        userMapper = mapper;
    }

    public static AuthUser currentUser() {
        Long userId = currentUserId();
        if (userMapper == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        return AuthUser.from(user);
    }

    public static Long currentUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
    }

    public static boolean isAdmin() {
        try {
            return StpUtil.hasRole("ADMIN");
        } catch (Exception e) {
            return false;
        }
    }
}
