package com.healthkb;

import com.healthkb.common.EmergencyRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergencyRulesTest {

    @Test
    void singleRedFlagIsEmergency() {
        assertTrue(EmergencyRules.isEmergency("患者昏迷、呼之不应"));
        assertEquals("昏迷", EmergencyRules.match("患者昏迷、呼之不应"));
        assertTrue(EmergencyRules.isEmergency("怀疑是中毒了"));
    }

    @Test
    void chestPainAloneIsNotEmergencyButWithCompanionIs() {
        // 单纯胸痛不升级，避免把普通肋间痛也推去急诊
        assertFalse(EmergencyRules.isEmergency("最近偶尔有点胸痛"));
        assertTrue(EmergencyRules.isEmergency("突然胸痛并且呼吸困难，出了很多汗"));
        assertEquals("胸痛伴呼吸困难", EmergencyRules.match("突然胸痛并且呼吸困难"));
    }

    @Test
    void ordinaryQuestionProducesNoBanner() {
        assertEquals("", EmergencyRules.banner("高血压平时饮食要注意什么"));
        assertEquals("", EmergencyRules.banner(null));
        assertEquals("", EmergencyRules.banner("   "));
    }

    @Test
    void bannerLeadsWithEmergencyInstructionAndNamesTheSymptom() {
        String banner = EmergencyRules.banner("老人突然昏迷了怎么办");
        assertTrue(banner.startsWith("> "), "应是 Markdown 引用块，实际: " + banner);
        assertTrue(banner.contains("120"));
        assertTrue(banner.contains("昏迷"), "提示里应点出命中的症状");
        assertTrue(banner.endsWith("\n\n"), "后面要接正文，需留出空行");
    }
}
