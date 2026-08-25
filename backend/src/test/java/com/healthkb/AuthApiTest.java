package com.healthkb;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void registerAndLoginSuccess() throws Exception {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String body = """
                {"username":"%s","password":"Pass123!","nickname":"测试"}
                """.formatted(username);
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode regJson = objectMapper.readTree(reg.getResponse().getContentAsString());
        assertEquals(0, regJson.path("code").asInt());
        assertEquals(username, regJson.path("data").path("username").asText());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Pass123!"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(login.getResponse().getContentAsString());
        assertEquals(0, loginJson.path("code").asInt());
        assertJwt(loginJson.path("data").path("token").asText());
    }

    /** Sa-Token 必须处于 JWT 模式（StpLogicJwtConfig），token 应是三段式 header.payload.signature。 */
    private static void assertJwt(String token) {
        assertTrue(token.length() > 20, "token 过短: " + token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "期望三段式 JWT，实际: " + token);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("loginId"), "JWT payload 缺少 loginId: " + payload);
    }

    @Test
    void duplicateUsernameRejected() throws Exception {
        String username = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String body = """
                {"username":"%s","password":"Pass123!"}
                """.formatted(username);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void loginIgnoresInvalidBearer() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer not-a-valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(login.getResponse().getContentAsString());
        assertEquals(0, json.path("code").asInt());
        assertJwt(json.path("data").path("token").asText());
    }

    @Test
    void seedAdminCanLogin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(login.getResponse().getContentAsString());
        assertEquals("ADMIN", json.path("data").path("user").path("role").asText());
    }
}
