package com.healthkb;

import com.healthkb.dto.TriageDtos;
import com.healthkb.service.TriageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TriageTest {

    @Autowired
    TriageService triageService;

    @Test
    void emergencyChestPainAndDyspneaGoesToEmergency() {
        TriageDtos.Request req = new TriageDtos.Request();
        req.setSymptoms("突然胸痛并且呼吸困难，出了很多汗");
        req.setAge(58);
        req.setSex("男");
        TriageDtos.Response resp = triageService.triage(req);
        assertEquals("emergency", resp.getUrgency());
        assertEquals("急诊科", resp.getDepartments().get(0).getDepartment());
        assertTrue(resp.getSummary().contains("仅供健康科普参考"));
    }

    @Test
    void comaKeywordIsEmergency() {
        TriageDtos.Request req = new TriageDtos.Request();
        req.setSymptoms("患者昏迷、呼之不应");
        assertTrue(triageService.isEmergency(req.getSymptoms()));
        assertEquals("急诊科", triageService.triage(req).getDepartments().get(0).getDepartment());
    }
}
