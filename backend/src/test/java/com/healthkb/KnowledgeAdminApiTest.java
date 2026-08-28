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
 * 后台知识库主流程：录入 → 分页列表可见 → 检索能召回 → 删除后不再召回。
 * 检索走哈希兜底 + lexical-filter 模式（测试环境无 embedding 服务），
 * 关键词用生僻组合避免命中既有 WHO 语料。
 */
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeAdminApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    private String loginAdmin() throws Exception {
        ApiTestClient admin = ApiTestClient.register(mockMvc, objectMapper);
        // 注册的用户是 USER 角色，管理接口需要 ADMIN：用种子管理员
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("token").asText();
    }

    @Test
    void addListSearchDeleteLifecycle() throws Exception {
        String token = loginAdmin();
        String keyword = "锆石矿"; // 生僻词，避免撞上已有语料
        String content = "关于" + keyword + "作业的职业防护：佩戴防尘口罩，定期职业健康体检，"
                + "作业场所保持通风。本条目为集成测试专用。";

        MvcResult add = mockMvc.perform(post("/api/admin/knowledge/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"职业防护测试条目\",\"category\":\"职业健康\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long docId = objectMapper.readTree(add.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asLong();
        assertTrue(docId > 0);

        // 分页列表能翻到它
        MvcResult list = mockMvc.perform(get("/api/admin/knowledge")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(list.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        assertEquals(20, data.path("size").asInt());
        assertTrue(data.path("total").asLong() >= 1);
        boolean found = false;
        for (JsonNode record : data.path("records")) {
            if (record.path("id").asLong() == docId) {
                found = true;
            }
        }
        assertTrue(found, "新录入的文档应出现在第一页（按创建时间倒序）");

        // 检索能召回（哈希向量 + 词面过滤：标题与正文都含关键词）
        assertTrue(searchReturns(token, keyword, docId), "删除前应能检索到");

        mockMvc.perform(delete("/api/admin/knowledge/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(!searchReturns(token, keyword, docId), "删除后不应再召回");
    }

    @Test
    void nonAdminCannotManageKnowledge() throws Exception {
        ApiTestClient normalUser = ApiTestClient.register(mockMvc, objectMapper);
        mockMvc.perform(get("/api/admin/knowledge")
                        .header("Authorization", "Bearer " + normalUser.token))
                .andExpect(status().isForbidden());
    }

    private boolean searchReturns(String token, String q, long docId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/knowledge/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", q))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode hits = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        for (JsonNode hit : hits) {
            if (hit.path("title").asText().contains("职业防护测试条目")) {
                return true;
            }
        }
        return false;
    }
}
