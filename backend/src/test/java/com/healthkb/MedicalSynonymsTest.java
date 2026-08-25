package com.healthkb;

import com.healthkb.rag.MedicalSynonyms;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicalSynonymsTest {

    @Test
    void expandsBothDirections() {
        assertTrue(MedicalSynonyms.expand(List.of("心梗")).contains("心肌梗死"));
        assertTrue(MedicalSynonyms.expand(List.of("心肌梗死")).contains("心梗"));
        assertTrue(MedicalSynonyms.expand(List.of("拉肚子")).contains("腹泻"));
    }

    @Test
    void keepsOriginalTermsAndDoesNotInventUnrelatedOnes() {
        List<String> out = MedicalSynonyms.expand(List.of("喉咙", "背部酸痛"));
        assertTrue(out.contains("喉咙"));
        assertTrue(out.contains("背部酸痛"));
        // 咽痛类查询不该把高血压/糖尿病拉进来，否则引用就会串台
        assertFalse(out.contains("高血压"));
        assertFalse(out.contains("糖尿病"));
    }

    @Test
    void unknownTermPassesThroughUnchanged() {
        assertTrue(MedicalSynonyms.expand(List.of("背部酸痛")).size() == 1);
        assertTrue(MedicalSynonyms.expand(List.of()).isEmpty());
    }
}
