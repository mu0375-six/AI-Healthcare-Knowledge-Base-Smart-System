package com.healthkb;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录是唯一的匿名密码校验入口，必须有限流。把限额压到 2 次/窗口，
 * 第 3 次连密码对错都不该看 —— 直接 429。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.auth.limit=2",
        "app.rate-limit.auth.window-seconds=60"
})
class AuthApiRateLimitTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void thirdLoginWithinWindowIsRejected429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                    // 密码错误走 401/400 都行，关键是没被限流拦下
                    .andExpect(status().is4xxClientError());
        }

        MvcResult blocked = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\",\"password\":\"User123!\"}"))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        var node = objectMapper.readTree(blocked.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(429, node.path("code").asInt());
        assertTrue(node.path("message").asText().contains("频繁"));
    }
}
