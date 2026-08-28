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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteApiTest {

    private static final Pattern DONE_MESSAGE_ID = Pattern.compile("\"messageId\":(\\d+)");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private long askForMessageId(ApiTestClient client) throws Exception {
        String sse = client.ask("高血压怎么控制", null);
        Matcher m = DONE_MESSAGE_ID.matcher(sse);
        assertTrue(m.find(), "SSE 流里应带 messageId: " + sse);
        return Long.parseLong(m.group(1));
    }

    @Test
    void duplicateAddIsIdempotent() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);
        long messageId = askForMessageId(client);

        long first = addFavorite(client, messageId);
        long second = addFavorite(client, messageId);

        assertEquals(first, second, "重复收藏同一消息应返回同一条记录，不产生第二条");
    }

    @Test
    void strangerCannotDeleteMyFavorite() throws Exception {
        ApiTestClient owner = ApiTestClient.register(mockMvc, objectMapper);
        ApiTestClient stranger = ApiTestClient.register(mockMvc, objectMapper);
        long favoriteId = addFavorite(owner, askForMessageId(owner));

        mockMvc.perform(delete("/api/favorites/" + favoriteId)
                        .header("Authorization", "Bearer " + stranger.token))
                .andExpect(status().isNotFound());

        // 本人仍可删
        mockMvc.perform(delete("/api/favorites/" + favoriteId)
                        .header("Authorization", "Bearer " + owner.token))
                .andExpect(status().isOk());
    }

    @Test
    void strangerSeesOnlyOwnFavorites() throws Exception {
        ApiTestClient owner = ApiTestClient.register(mockMvc, objectMapper);
        ApiTestClient stranger = ApiTestClient.register(mockMvc, objectMapper);
        addFavorite(owner, askForMessageId(owner));

        MvcResult res = mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + stranger.token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(0, data.path("records").size());
        assertEquals(0, data.path("total").asLong());
    }

    @Test
    void listReturnsContentAndPaginationFields() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);
        addFavorite(client, askForMessageId(client));

        MvcResult res = mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(1, data.path("total").asLong());
        JsonNode record = data.path("records").get(0);
        assertTrue(record.path("content").asText().length() > 0, "收藏列表应带回答内容");
        assertNotEquals(0, record.path("messageId").asLong());
    }

    private long addFavorite(ApiTestClient client, long messageId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + client.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":" + messageId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
    }
}
