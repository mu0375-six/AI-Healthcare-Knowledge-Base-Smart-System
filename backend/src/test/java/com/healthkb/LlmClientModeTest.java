package com.healthkb;

import com.healthkb.rag.LlmClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 请求 URL 拼装已交给 Spring AI（spring.ai.openai.base-url），这里只保留答题模式判定。
 */
class LlmClientModeTest {

    @Test
    void metforminQuestionUsesProfessionalMode() {
        assertTrue(LlmClient.looksProfessional("二甲双胍有什么注意事项"));
        assertTrue(LlmClient.looksProfessional("高血压要怎么治疗"));
        assertFalse(LlmClient.looksProfessional("嗓子痛怎么办"));
        assertFalse(LlmClient.looksProfessional(""));
    }
}
