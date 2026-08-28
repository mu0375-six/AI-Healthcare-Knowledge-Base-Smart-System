package com.healthkb.rag;

import com.healthkb.common.MedicalConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 基于 Spring AI 2.0 {@link ChatClient} 的医学问答生成器。
 *
 * <p>底层模型由 {@code spring.ai.openai.*} 自动配置（默认 DeepSeek），
 * 多模态图片走 {@link Media}。外部模型不可用或未配置时回退 {@link TemplateAnswerer}，
 * 保证没有 API key / 断网时依然能出答案。
 */
@Slf4j
@Component
public class LlmClient {

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final TemplateAnswerer templateAnswerer;

    public LlmClient(ObjectProvider<ChatClient> chatClientProvider, TemplateAnswerer templateAnswerer) {
        this.chatClientProvider = chatClientProvider;
        this.templateAnswerer = templateAnswerer;
    }

    public record ImageInput(String base64Data, String mimeType) {
    }

    @Value("${app.llm.base-url:}")
    private String baseUrl;

    @Value("${app.llm.api-key:}")
    private String apiKey;

    @Value("${app.llm.model:deepseek-v4-flash-vision-exp}")
    private String model;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && chatClientProvider.getIfAvailable() != null;
    }

    public void generate(String question, List<ScoredChunk> chunks, List<ChatTurn> history,
                         String extraContext, AnswerSink sink) {
        generate(question, chunks, history, extraContext, List.of(), sink);
    }

    public void generate(String question, List<ScoredChunk> chunks, List<ChatTurn> history,
                         String extraContext, List<ImageInput> images, AnswerSink sink) {
        if (isConfigured()) {
            try {
                streamRemote(question, chunks, history, extraContext, images, sink);
                return;
            } catch (Exception e) {
                log.warn("Spring AI 调用失败，回退模板回答: {}", e.toString());
            }
        }
        String full = templateAnswerer.answer(question, chunks, history, extraContext);
        templateAnswerer.stream(full, sink);
    }

    public String generateSync(String question, List<ScoredChunk> chunks, List<ChatTurn> history, String extraContext) {
        StringBuilder sb = new StringBuilder();
        generate(question, chunks, history, extraContext, sb::append);
        String out = sb.toString();
        if (!out.contains("仅供健康科普参考")) {
            out = out + "\n\n" + MedicalConstants.DISCLAIMER;
        }
        return out;
    }

    /**
     * 报告图片 → 结构化文本（拍照识别化验单）。
     * 走同一多模态 ChatClient：不引入额外的 OCR 引擎/模型包，离线缺省时由调用方降级。
     *
     * @throws IllegalStateException 未配置模型 / 模型不支持图像 / 识别结果为空时抛出，由调用方转友好提示
     */
    public String extractReportText(byte[] imageBytes, String mimeType) {
        if (!isConfigured()) {
            throw new IllegalStateException("未配置多模态模型（APP_LLM_BASE_URL / APP_LLM_API_KEY）");
        }
        if (!modelSupportsVision()) {
            throw new IllegalStateException("当前模型「" + model + "」不支持图像输入");
        }
        List<Media> media = toMedia(List.of(new ImageInput(
                Base64.getEncoder().encodeToString(imageBytes), mimeType)));
        if (media.isEmpty()) {
            throw new IllegalStateException("图片字节解析失败");
        }
        UserMessage message = UserMessage.builder()
                .text("""
                        你是体检报告的图片转写助手。请把图片中化验单/体检报告的文字**逐行转写**成纯文本：
                        - 保留指标名、数值、单位、参考范围与箭头标记，按原文顺序一行一条；
                        - 忽略医院抬头、水印、签字等与指标无关的内容；
                        - 看不清或不确定的内容标 [不确定]；
                        - 不要解读、不要总结、不要加任何解释性文字，只输出转写结果。
                        """)
                .media(media)
                .build();
        String text;
        try {
            text = chatClientProvider.getObject().prompt()
                    .messages(List.of(
                            new SystemMessage("你是医学文档图片转写工具。只输出图片上文字的转写结果，禁止解读、总结或输出转写以外的任何内容。"),
                            message))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("报告图片识别失败: {}", e.toString());
            throw new IllegalStateException("图片识别失败，请重试或直接上传文本报告");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("图片识别结果为空");
        }
        return text.trim();
    }

    private void streamRemote(String question, List<ScoredChunk> chunks, List<ChatTurn> history,
                              String extraContext, List<ImageInput> images, AnswerSink sink) {
        ChatClient chatClient = chatClientProvider.getObject();
        StringBuilder acc = new StringBuilder();

        // 走 messages(...) 而非 user(...)：后者会过一遍 StringTemplate 渲染，
        // 医学摘录里出现的 { } $ 会被当成模板占位符解析失败。
        // try-with-resources：客户端中断（sink 抛异常）时关闭 Stream 以取消上游订阅，
        // 否则到模型的 HTTP 连接会一直挂到超时。
        try (var tokens = chatClient.prompt()
                .messages(buildMessages(question, chunks, history, extraContext, images))
                .stream()
                .content()
                .toStream()) {
            tokens.forEach(token -> {
                if (token != null && !token.isEmpty()) {
                    acc.append(token);
                    sink.delta(token);
                }
            });
        }

        if (acc.isEmpty()) {
            throw new IllegalStateException("LLM 空响应");
        }
        if (!acc.toString().contains("仅供健康科普参考")) {
            sink.delta("\n\n" + MedicalConstants.DISCLAIMER);
        }
    }

    private List<Message> buildMessages(String question, List<ScoredChunk> chunks, List<ChatTurn> history,
                                        String extraContext, List<ImageInput> images) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt()));
        if (history != null) {
            for (ChatTurn t : history) {
                String content = t.getContent() == null ? "" : t.getContent();
                if ("assistant".equalsIgnoreCase(t.getRole())) {
                    messages.add(new AssistantMessage(content));
                } else {
                    messages.add(new UserMessage(content));
                }
            }
        }
        String prompt = buildUserPrompt(question, chunks, extraContext);
        List<Media> media = toMedia(images);
        messages.add(media.isEmpty()
                ? new UserMessage(prompt)
                : UserMessage.builder().text(prompt).media(media).build());
        return messages;
    }

    private List<Media> toMedia(List<ImageInput> images) {
        List<Media> media = new ArrayList<>();
        if (images == null || images.isEmpty() || !modelSupportsVision()) {
            return media;
        }
        for (ImageInput img : images) {
            try {
                byte[] bytes = Base64.getDecoder().decode(img.base64Data());
                media.add(new Media(mimeTypeOf(img.mimeType()), new ByteArrayResource(bytes)));
            } catch (Exception e) {
                log.warn("图片转 Media 失败，已跳过: {}", e.toString());
            }
        }
        return media;
    }

    private static MimeType mimeTypeOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        try {
            return MimeTypeUtils.parseMimeType(raw);
        } catch (Exception e) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
    }

    private String systemPrompt() {
        return """
                你是「康识」医学科普助手，面向患者做规范、审慎的健康教育，使用简体中文。
                先判断问题类型，再选择写法。知识库摘录只作依据：相关则吸收，无关则完全忽略，禁止整段粘贴、禁止编造指南名称。

                【A. 用药 / 疾病机制 / 检查指标 / 注意事项】
                按临床宣教口径写，专业、条理清楚，约 500–800 字。必须覆盖用户真正问到的点，并补全关键风险信息。
                建议小标题（按需取舍，不要空喊口号）：
                - 简要定位（这是什么药/病、主要用途）
                - 使用注意与常见不良反应
                - 禁忌、慎用与相互作用
                - 监测与生活配合
                - 何时停药并就医
                术语可保留（如乳酸酸中毒、eGFR、造影剂），首次出现用括号作一句人话解释。
                严禁写出具体剂量、疗程、给药频次；一律写「剂量与是否适用须由医师根据肾功能等评估后决定」。
                处方药强调遵医嘱，不可自行加减停。

                【B. 日常症状（嗓子痛、头晕、咳嗽、发烧等）】
                口语、简短，400 字内：先说常见情况 → 家里可做什么 → 危险信号马上去医院。

                共同规则：不做确诊、不开处方。结尾必须原样输出：
                %s
                """.formatted(MedicalConstants.DISCLAIMER);
    }

    private String buildUserPrompt(String question, List<ScoredChunk> chunks, String extraContext) {
        StringBuilder sb = new StringBuilder();
        boolean professional = looksProfessional(question);
        sb.append(professional
                ? "本题按【A. 用药/疾病/注意事项】作答，要专业、完整，不要写成口语闲聊。\n"
                : "本题按【B. 日常症状】作答，简洁即可。\n");
        sb.append("用户问题：").append(question).append("\n\n");
        if (chunks == null || chunks.isEmpty()) {
            sb.append("没有足够相关的知识库摘录。请依据公认临床常识作答，不要编造指南或文献名称。\n");
        } else {
            sb.append("相关知识库摘录（请优先依据这些内容组织答案，不要复述与问题无关的段落）：\n");
            int limit = Math.min(professional ? 4 : 2, chunks.size());
            int clip = professional ? 900 : 320;
            for (int i = 0; i < limit; i++) {
                ScoredChunk c = chunks.get(i);
                sb.append("[").append(i + 1).append("] ")
                        .append(c.getTitle()).append("（").append(nullToEmpty(c.getSource())).append("）\n")
                        .append(brief(c.getContent(), clip)).append("\n\n");
            }
        }
        if (extraContext != null && !extraContext.isBlank()) {
            sb.append("补充上下文：\n").append(brief(extraContext, 800)).append("\n");
        }
        return sb.toString();
    }

    private boolean modelSupportsVision() {
        return model != null && model.toLowerCase(Locale.ROOT).contains("vision");
    }

    public static boolean looksProfessional(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.trim();
        String[] keys = {
                "注意", "不良", "禁忌", "慎用", "相互作用", "副作用", "适应证", "适应症",
                "二甲双胍", "阿司匹林", "氨氯地平", "用药", "药品", "说明书",
                "诊断", "治疗", "机制", "并发症", "指标", "参考范围", "指南",
                "高血压", "糖尿病", "冠心病", "慢阻肺", "哮喘"
        };
        for (String k : keys) {
            if (q.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String brief(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
