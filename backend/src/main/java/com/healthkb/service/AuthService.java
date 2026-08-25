package com.healthkb.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthkb.common.AppException;
import com.healthkb.common.MedicalConstants;
import com.healthkb.dto.AuthDtos;
import com.healthkb.entity.SysUser;
import com.healthkb.mapper.SysUserMapper;
import com.healthkb.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;

    public AuthDtos.UserView register(AuthDtos.RegisterRequest req) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (exists != null && exists > 0) {
            throw AppException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPassword(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getUsername().trim() : req.getNickname().trim());
        user.setRole(MedicalConstants.ROLE_USER);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return AuthDtos.UserView.from(user);
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername().trim()));
        if (user == null || !BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw AppException.unauthorized("用户名或密码错误");
        }
        StpUtil.login(user.getId());
        AuthDtos.LoginResponse resp = new AuthDtos.LoginResponse();
        resp.setToken(StpUtil.getTokenValue());
        resp.setUser(AuthDtos.UserView.from(user));
        return resp;
    }

    public AuthDtos.UserView me() {
        SysUser user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        return AuthDtos.UserView.from(user);
    }
}
