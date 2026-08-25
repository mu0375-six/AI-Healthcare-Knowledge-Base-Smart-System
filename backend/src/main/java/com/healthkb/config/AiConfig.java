package com.healthkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring AI 2.0 接入点。
 *
 * <p>对话侧：把自动配置好的 {@link ChatModel}（spring.ai.openai.*，默认 DeepSeek）包成 {@link ChatClient}，
 * 供 {@code LlmClient} 做流式问答。
 *
 * <p>向量侧：DeepSeek 不提供 embedding 端点，所以不能复用对话那套 base-url，
 * 这里按 {@code app.embedding.*} 单独构建一个 {@link OpenAiEmbeddingModel}
 * （例如硅基流动 BAAI/bge-m3、阿里百炼 text-embedding-v3）。
 * 未配置 api-key 时不注册该 Bean，{@code VectorizeService} 会自动退回哈希向量。
 */
@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public EmbeddingModel medicalEmbeddingModel(
            @Value("${app.embedding.base-url:}") String baseUrl,
            @Value("${app.embedding.api-key:}") String apiKey,
            @Value("${app.embedding.model:BAAI/bge-m3}") String model,
            @Value("${app.embedding.timeout-seconds:30}") int timeoutSeconds) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("未配置 app.embedding.base-url / api-key，医学知识向量化将使用哈希兜底向量（无语义能力）");
            return null;
        }
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .build();
        log.info("医学向量化模型已启用：{} @ {}", model, baseUrl);
        return OpenAiEmbeddingModel.builder().options(options).build();
    }
}
