package com.healthkb.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试共用客户端：注册一个全新用户（独立限流配额、独立数据），
 * 返回其 token。每个测试自己注册，互不干扰。
 */
public final class ApiTestClient {

    public final MockMvc mockMvc;
    public final ObjectMapper objectMapper;
    public final String username;
    public final String token;

    private ApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper, String username, String token) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.username = username;
        this.token = token;
    }

    public static ApiTestClient register(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        String username = "t" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000);
        String password = "Passw0rd!";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(login.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new ApiTestClient(mockMvc, objectMapper, username, json.path("data").path("token").asText());
    }

    /** 提一个问题并等流式结束（测试环境无 LLM，走模板回答，离线可跑）。返回完整 SSE 文本。 */
    public String ask(String question, Long sessionId) throws Exception {
        String body = sessionId == null
                ? "{\"question\":\"" + question + "\"}"
                : "{\"question\":\"" + question + "\",\"sessionId\":" + sessionId + "}";
        MvcResult started = mockMvc.perform(post("/api/chat/ask")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult finished = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();
        return new String(finished.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
