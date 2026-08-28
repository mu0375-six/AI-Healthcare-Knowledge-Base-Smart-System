package com.healthkb;

import com.healthkb.support.ApiTestClient;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会话的横向越权与级联删除，以及分页参数是否真实生效。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatApiPrivacyTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private long createSession(ApiTestClient client) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + client.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }

    @Test
    void otherUserCannotReadOrDeleteMySession() throws Exception {
        ApiTestClient owner = ApiTestClient.register(mockMvc, objectMapper);
        ApiTestClient stranger = ApiTestClient.register(mockMvc, objectMapper);
        long sessionId = createSession(owner);

        mockMvc.perform(get("/api/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + stranger.token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/chat/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + stranger.token))
                .andExpect(status().isNotFound());

        // 本人访问不受影响
        mockMvc.perform(get("/api/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + owner.token))
                .andExpect(status().isOk());
    }

    @Test
    void deleteSessionCascadesMessages() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);
        long sessionId = createSession(client);
        String sse = client.ask("高血压怎么控制", sessionId);
        assertTrue(sse.contains("event:"));

        JsonNode messages = listMessages(client, sessionId, null, null);
        assertTrue(messages.path("data").path("total").asLong() >= 2, "问答后应至少有用户与助手两条消息");

        mockMvc.perform(delete("/api/chat/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isNotFound());
    }

    @Test
    void messagePaginationReturnsNewestFirstInSmallPages() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);
        long sessionId = createSession(client);
        client.ask("第一个问题血压", sessionId);
        client.ask("第二个问题血糖", sessionId);
        client.ask("第三个问题用药", sessionId);

        JsonNode page1 = listMessages(client, sessionId, 1, 2);
        JsonNode page2 = listMessages(client, sessionId, 2, 2);

        assertEquals(2, page1.path("data").path("records").size());
        assertEquals(6, page1.path("data").path("total").asLong(), "三轮问答应有 6 条消息");
        assertEquals(2, page2.path("data").path("records").size());

        // 第 1 页是最新两条，且块内按时间正序（与历史一致，方便前端直接渲染）
        String firstPageNewest = page1.path("data").path("records").get(1).path("content").asText();
        assertTrue(firstPageNewest.contains("第三个问题"), "第 1 页末尾应是最新一条提问");
        // 6 条消息、每页 2 条：第 2 页 = 最新数的第 3、4 条（第二轮问答），页首应是最早那条提问
        String page2Oldest = page2.path("data").path("records").get(0).path("content").asText();
        assertTrue(page2Oldest.contains("第二个问题"), "第 2 页开头应是第二轮的提问");
    }

    @Test
    void sessionListIsPaginated() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);
        for (int i = 0; i < 3; i++) {
            createSession(client);
        }
        MvcResult res = mockMvc.perform(get("/api/chat/sessions")
                        .header("Authorization", "Bearer " + client.token)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(2, data.path("records").size());
        assertTrue(data.path("total").asLong() >= 3);
        assertEquals(2, data.path("size").asInt());
    }

    private JsonNode listMessages(ApiTestClient client, long sessionId, Integer page, Integer size) throws Exception {
        var request = get("/api/chat/sessions/" + sessionId + "/messages")
                .header("Authorization", "Bearer " + client.token);
        if (page != null) {
            request.param("page", String.valueOf(page));
        }
        if (size != null) {
            request.param("size", String.valueOf(size));
        }
        MvcResult res = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
