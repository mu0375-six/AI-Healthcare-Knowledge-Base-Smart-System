package com.healthkb;

import com.healthkb.support.ApiTestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 复测《改进纪要》5.2 / 《改进清单》阶段⓪ 的「体检指标重复写入」缺陷：
 * 同一份报告「上传时勾选档案自动导入」与「详情页点写入档案」两条路径对同一指标
 * 只应留一条记录（幂等键：profileId + 报告创建时间 + 来源备注）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportImportIdempotencyTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    /** 与 samples/demo-report.txt 同构的演示报告：4 个可导入指标 + 2 个不可导入指标。 */
    private static final String DEMO_REPORT = """
            演示体检报告
            姓名：演示
            空腹血糖 7.2 mmol/L (3.9-6.1)
            餐后血糖 9.8 mmol/L (0-7.8)
            收缩压 148 mmHg (90-139)
            舒张压 92 mmHg (60-89)
            总胆固醇 5.8 mmol/L (0-5.2)
            甘油三酯 2.1 mmol/L (0-1.7)
            """;

    @Test
    void sameReportImportedTwiceKeepsOneMetricEach() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);

        // 建档案（接口在无档案时自动创建默认档案）
        MvcResult profileRes = mockMvc.perform(put("/api/health/profile")
                        .header("Authorization", "Bearer " + client.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"演示\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long profileId = objectMapper.readTree(profileRes.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();

        // 第一次：上传报告并勾选档案，自动导入
        MvcResult up = mockMvc.perform(multipart("/api/reports/upload")
                        .file(new MockMultipartFile("file", "demo-report.txt",
                                "text/plain", DEMO_REPORT.getBytes(StandardCharsets.UTF_8)))
                        .param("profileId", String.valueOf(profileId))
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isOk())
                .andReturn();
        long reportId = objectMapper.readTree(up.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("report").path("id").asLong();

        // 第二次：报告详情页「写入档案」再导一次（原 bug 的第二条路径）
        mockMvc.perform(post("/api/reports/" + reportId + "/import")
                        .param("profileId", String.valueOf(profileId))
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isOk());

        // 档案里 4 个可导入指标各一条，类型不重复
        MvcResult metrics = mockMvc.perform(get("/api/health/metrics")
                        .header("Authorization", "Bearer " + client.token)
                        .param("profileId", String.valueOf(profileId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = objectMapper.readTree(metrics.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(4, arr.size(), "两次导入后应只有 4 条指标（空腹血糖/餐后血糖/收缩压/舒张压）");
        Set<String> types = new HashSet<>();
        arr.forEach(m -> types.add(m.path("metricType").asText()));
        assertEquals(4, types.size(), "同一指标不应出现两条；实际: " + types);
    }
}
