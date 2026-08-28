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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 首页健康总览的「需要留心」聚合：只汇集最新异常指标，正常值不出现，
 * 并带参考范围、档案名与同类型差值。
 */
@SpringBootTest
@AutoConfigureMockMvc
class HomeOverviewAlertTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void overviewCollectsLatestAbnormalMetricsOnly() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);

        MvcResult profileRes = mockMvc.perform(put("/api/health/profile")
                        .header("Authorization", "Bearer " + client.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"爸爸\",\"relation\":\"父亲\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long profileId = objectMapper.readTree(profileRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        addMetric(client.token, profileId, "空腹血糖", 7.2, "2026-08-01T08:00:00");
        addMetric(client.token, profileId, "收缩压", 148, "2026-08-01T08:05:00");
        addMetric(client.token, profileId, "舒张压", 70, "2026-08-01T08:06:00"); // 正常，不应出现
        addMetric(client.token, profileId, "体重", 70, "2026-08-01T08:07:00");   // 正常，不应出现

        MvcResult res = mockMvc.perform(get("/api/home/overview")
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");

        JsonNode alerts = data.path("alerts");
        assertEquals(2, alerts.size(), "只应有两条异常指标，正常值不进入告警列表");
        boolean foundFasting = false;
        boolean foundSystolic = false;
        for (JsonNode a : alerts) {
            assertTrue(a.path("profileName").asText().contains("爸爸"), "告警应带档案名");
            if ("空腹血糖".equals(a.path("metricType").asText())) {
                foundFasting = true;
                assertEquals("high", a.path("flag").asText());
                assertEquals(7.2, a.path("metricValue").asDouble(), 1e-9);
                assertTrue(a.path("refRange").asText().contains("3.9-6.1"), "应带参考范围");
            }
            if ("收缩压".equals(a.path("metricType").asText())) {
                foundSystolic = true;
                assertEquals("high", a.path("flag").asText());
            }
        }
        assertTrue(foundFasting && foundSystolic, "告警列表应同时包含血糖与血压异常");
    }

    private void addMetric(String token, long profileId, String type, double value, String recordedAt) throws Exception {
        mockMvc.perform(post("/api/health/metrics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":" + profileId + ",\"metricType\":\"" + type
                                + "\",\"value\":" + value + ",\"recordedAt\":\"" + recordedAt + "\"}"))
                .andExpect(status().isOk());
    }
}
