package com.healthkb.rag;

import com.healthkb.entity.KbChunk;
import com.healthkb.entity.KbDocument;
import com.healthkb.mapper.KbChunkMapper;
import com.healthkb.mapper.KbDocumentMapper;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 启动重建的决策逻辑：绝大多数重启里知识没变，不该再花一遍远程 embedding。
 */
class RebuildDecisionTest {

    private final VectorizeService vectorize = mock(VectorizeService.class);
    private final ContentRetriever retriever = mock(ContentRetriever.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final VectorStore memoryStore = mock(VectorStore.class);
    private final KbChunkMapper chunkMapper = mock(KbChunkMapper.class);
    private final KbDocumentMapper documentMapper = mock(KbDocumentMapper.class);

    private final RagService service = new RagService(
            vectorize, retriever, vectorStore, memoryStore, chunkMapper, documentMapper);

    @Test
    void freshCountsSkipEmbeddingAndHydrateMemoryBackupOnly() {
        when(chunkMapper.selectCount(null)).thenReturn(3L);
        when(vectorStore.size()).thenReturn(3);
        when(vectorStore.loadAll()).thenReturn(stored(3));

        boolean rebuilt = service.rebuildIfStale(false);

        assertFalse(rebuilt, "条数一致且回灌成功时不应重建");
        verifyNoInteractions(vectorize);
        verify(vectorStore, never()).clear();
        verify(memoryStore, times(3)).upsert(anyLong(), anyLong(), any(float[].class),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void countMismatchFallsBackToFullRebuild() {
        when(chunkMapper.selectCount(null)).thenReturn(5L);
        when(vectorStore.size()).thenReturn(88);
        stubRebuildInputs();

        boolean rebuilt = service.rebuildIfStale(false);

        assertTrue(rebuilt);
        verify(vectorStore).clear();
        verify(vectorize, times(2)).embed(anyString());
    }

    @Test
    void loadAllShortfallAlsoForcesFullRebuild() {
        // 数量相等但读回来的行数对不上（如 Milvus 查询半途失败）：宁重建不留残缺内存库
        when(chunkMapper.selectCount(null)).thenReturn(3L);
        when(vectorStore.size()).thenReturn(3);
        when(vectorStore.loadAll()).thenReturn(stored(2));
        stubRebuildInputs();

        boolean rebuilt = service.rebuildIfStale(false);

        assertTrue(rebuilt);
        verify(vectorStore).clear();
    }

    @Test
    void forceFlagBypassesFreshnessCheck() {
        stubRebuildInputs();

        boolean rebuilt = service.rebuildIfStale(true);

        assertTrue(rebuilt);
        verify(chunkMapper, never()).selectCount(null);
        verify(vectorStore).clear();
    }

    private static List<StoredChunk> stored(int n) {
        List<StoredChunk> out = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            out.add(new StoredChunk(i, 1L, new float[]{0.1f}, "c", "t", "cat", "src"));
        }
        return out;
    }

    private void stubRebuildInputs() {
        KbChunk c1 = new KbChunk();
        c1.setId(1L);
        c1.setDocumentId(1L);
        c1.setContent("高血压是心血管疾病");
        KbChunk c2 = new KbChunk();
        c2.setId(2L);
        c2.setDocumentId(1L);
        c2.setContent("糖尿病需控制血糖");
        when(chunkMapper.selectList(null)).thenReturn(List.of(c1, c2));
        KbDocument doc = new KbDocument();
        doc.setId(1L);
        doc.setTitle("慢病防治");
        doc.setCategory("疾病指南");
        doc.setSource("WHO 实况报道");
        when(documentMapper.selectList(null)).thenReturn(List.of(doc));
        when(vectorize.embed(anyString())).thenReturn(new float[]{0.25f});
    }
}
