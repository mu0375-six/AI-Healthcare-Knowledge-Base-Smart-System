package com.healthkb;

import com.healthkb.entity.KbDocument;
import com.healthkb.mapper.KbDocumentMapper;
import com.healthkb.rag.OfficialHtmlCleaner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OfficialKnowledgeTest {

    @Autowired
    KbDocumentMapper documentMapper;

    @Test
    void htmlCleanerStripsTagsAndScripts() {
        String text = OfficialHtmlCleaner.toPlainText(
                "<html><script>alert(1)</script><h1>高血压</h1><p>140/90 mmHg</p></html>");
        assertTrue(text.contains("高血压"));
        assertTrue(text.contains("140/90"));
        assertFalse(text.contains("<p>"));
        assertFalse(text.contains("alert"));
    }

    @Test
    void seederLoadsWhoOrNhcDocuments() {
        List<KbDocument> docs = documentMapper.selectList(null);
        assertFalse(docs.isEmpty());
        boolean official = docs.stream().anyMatch(d ->
                (d.getPublisher() != null && (d.getPublisher().contains("卫生组织") || d.getPublisher().contains("卫生健康")))
                        || (d.getSourceUrl() != null && (d.getSourceUrl().contains("who.int") || d.getSourceUrl().contains("nhc.gov.cn"))));
        assertTrue(official, "应入库 WHO 或国家卫健委文档");
        boolean metformin = docs.stream().anyMatch(d ->
                "糖尿病".equals(d.getTitle()) || (d.getSourceUrl() != null && d.getSourceUrl().contains("diabetes")));
        assertTrue(metformin, "应包含世卫组织糖尿病条目");
    }
}
