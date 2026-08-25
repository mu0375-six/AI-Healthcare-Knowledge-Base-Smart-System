package com.healthkb.dto;

import com.healthkb.entity.SysUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDtos {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度为 3-32")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度为 6-64")
        private String password;

        @Size(max = 32, message = "昵称过长")
        private String nickname;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class UserView {
        private Long id;
        private String username;
        private String nickname;
        private String role;

        public static UserView from(SysUser u) {
            UserView v = new UserView();
            v.id = u.getId();
            v.username = u.getUsername();
            v.nickname = u.getNickname();
            v.role = u.getRole();
            return v;
        }
    }

    @Data
    public static class LoginResponse {
        private String token;
        private UserView user;
    }
}
