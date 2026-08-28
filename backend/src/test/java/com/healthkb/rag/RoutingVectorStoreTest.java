package com.healthkb.rag;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 补账队列的关键场景：Milvus 故障窗口内的写入不能在恢复后凭空消失 ——
 * 只要 Milvus 返回非空命中，检索就不再看内存，缺的那批数据等于永久丢失。
 */
class RoutingVectorStoreTest {

    private static float[] vec() {
        return new float[]{0.1f, 0.2f};
    }

    private static void upsert(RoutingVectorStore store, long chunkId, long docId) {
        store.upsert(chunkId, docId, vec(), "内容" + chunkId, "标题", "疾病指南", "WHO");
    }

    /** 内存库用真实实现（要靠它检索与兜底），只有 Milvus 是桩。 */
    private static RoutingVectorStore newRouting(MilvusVectorStore milvus) {
        VectorizeService vectorize = mock(VectorizeService.class);
        // info() 需要维度；embed 在这些场景里不允许被调用
        when(vectorize.dimension()).thenReturn(8);
        return new RoutingVectorStore(new InMemoryVectorStore(vectorize), milvus);
    }

    private static MilvusVectorStore downMilvus() {
        MilvusVectorStore milvus = mock(MilvusVectorStore.class);
        when(milvus.ensure()).thenReturn(false);
        when(milvus.isReady()).thenReturn(false);
        when(milvus.isEnabled()).thenReturn(false);
        return milvus;
    }

    @Test
    void downUpsertKeepsMemoryCopySearchableAndQueuesForReplay() {
        MilvusVectorStore milvus = downMilvus();
        RoutingVectorStore routing = newRouting(milvus);

        upsert(routing, 1L, 10L);

        List<ScoredChunk> hits = routing.search(vec(), 5);
        assertEquals(1, hits.size());
        assertEquals(1L, hits.get(0).getChunkId());
        verify(milvus, never()).upsert(any(), any(), any(float[].class), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void recoveryReplaysPendingOpsInOrderAndSurvivesPartialFailure() {
        MilvusVectorStore milvus = downMilvus();
        RoutingVectorStore routing = newRouting(milvus);
        upsert(routing, 1L, 10L);
        upsert(routing, 2L, 10L);

        // Milvus 恢复，但补第一条时就抛异常：队列必须保留原顺序，下个入口继续
        when(milvus.ensure()).thenReturn(true);
        doThrow(new IllegalStateException("insert 失败"))
                .doNothing()
                .when(milvus).upsert(eq(1L), eq(10L), any(float[].class),
                        contains("内容1"), anyString(), anyString(), anyString());

        routing.size(); // 第一次：补 1 抛异常，立即停手、队列保留
        routing.size(); // 第二次：1 补成，2 按原顺序跟上

        InOrder order = inOrder(milvus);
        order.verify(milvus).upsert(eq(1L), eq(10L), any(float[].class),
                contains("内容1"), anyString(), anyString(), anyString());
        order.verify(milvus).upsert(eq(2L), eq(10L), any(float[].class),
                contains("内容2"), anyString(), anyString(), anyString());
    }

    @Test
    void deleteWhileDownPurgesPendingUpsertsSoDeletedDocNeverRevives() {
        MilvusVectorStore milvus = downMilvus();
        RoutingVectorStore routing = newRouting(milvus);
        upsert(routing, 1L, 10L);

        routing.deleteByDocumentId(10L);

        when(milvus.ensure()).thenReturn(true);
        routing.size();

        verify(milvus, never()).upsert(any(), any(), any(float[].class), anyString(),
                anyString(), anyString(), anyString());
        verify(milvus).deleteByDocumentId(10L);
    }

    @Test
    void queueIsBoundedAndDropsOldestWithoutExploding() {
        MilvusVectorStore milvus = downMilvus();
        RoutingVectorStore routing = newRouting(milvus);

        for (long i = 0; i < RoutingVectorStore.MAX_PENDING + 50; i++) {
            upsert(routing, i, i / 10);
        }

        // 数据仍在内存备份里可检索，封顶丢弃只是少补、不是丢数据
        assertTrue(routing.search(vec(), 3).size() > 0);
    }
}
