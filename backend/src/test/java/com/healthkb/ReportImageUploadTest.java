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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 拍照识别化验单的降级路径：
 * - 测试环境未配置多模态模型 → 图片上传应返回带成因的 400，而不是含糊的失败；
 * - 附带 extractedText 的图片（用户手贴文本的兜底入口）→ 照常解析成功。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportImageUploadTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    /** 1×1 有效 PNG（含标准 PNG 签名，通过 FileMagic 校验）。 */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Test
    void imageWithoutModelConfiguredReturnsExplanatory400() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);
        MockMultipartFile img = new MockMultipartFile(
                "file", "xuechangdan.png", "image/png", PNG);
        MvcResult res = mockMvc.perform(multipart("/api/reports/upload")
                        .file(img)
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String message = objectMapper.readTree(body).path("message").asText();
        assertTrue(message.contains("模型") || message.contains("识别") || message.contains("文本"),
                "未配置模型时应提示配置多模态模型或改用文本上传，实际: " + message);
    }

    @Test
    void imageWithPastedTextStillParses() throws Exception {
        ApiTestClient client = ApiTestClient.register(mockMvc, objectMapper);
        String text = "空腹血糖 7.2 mmol/L (3.9-6.1)\n收缩压 148 mmHg (90-139)";
        MockMultipartFile img = new MockMultipartFile(
                "file", "xuechangdan.png", "image/png", PNG);
        MvcResult res = mockMvc.perform(multipart("/api/reports/upload")
                        .file(img)
                        .param("extractedText", text)
                        .header("Authorization", "Bearer " + client.token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertTrue(data.path("items").size() >= 2, "粘贴文本后图片应照常解析出指标");
    }
}
