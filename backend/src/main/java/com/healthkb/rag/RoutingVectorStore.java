package com.healthkb.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Primary
@Component
public class RoutingVectorStore implements VectorStore {

    private final VectorStore memory;
    private final MilvusVectorStore milvus;

    public RoutingVectorStore(@Qualifier("memoryVectorStore") VectorStore memory, MilvusVectorStore milvus) {
        this.memory = memory;
        this.milvus = milvus;
    }

    @Override
    public void upsert(Long chunkId, Long documentId, float[] vector, String content,
                       String title, String category, String source) {
        memory.upsert(chunkId, documentId, vector, content, title, category, source);
        try {
            if (milvus.ensure()) {
                milvus.upsert(chunkId, documentId, vector, content, title, category, source);
            }
        } catch (Exception e) {
            log.warn("写入 Milvus 失败，已保留内存副本: {}", e.getMessage());
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        memory.deleteByDocumentId(documentId);
        try {
            if (milvus.isReady() || milvus.ensure()) {
                milvus.deleteByDocumentId(documentId);
            }
        } catch (Exception e) {
            log.warn("Milvus 删除失败: {}", e.getMessage());
        }
    }

    @Override
    public List<ScoredChunk> search(float[] query, int topK) {
        try {
            if (milvus.ensure()) {
                List<ScoredChunk> hits = milvus.search(query, topK);
                if (!hits.isEmpty() || memory.size() == 0) {
                    return hits;
                }
            }
        } catch (Exception e) {
            log.warn("Milvus 检索失败，回退内存: {}", e.getMessage());
        }
        return memory.search(query, topK);
    }

    @Override
    public int size() {
        try {
            if (milvus.isReady()) {
                return milvus.size();
            }
        } catch (Exception ignored) {
        }
        return memory.size();
    }

    @Override
    public void clear() {
        memory.clear();
        try {
            if (milvus.ensure()) {
                milvus.clear();
            }
        } catch (Exception e) {
            log.warn("Milvus 清空失败: {}", e.getMessage());
        }
    }

    @Override
    public void flush() {
        try {
            if (milvus.isReady()) {
                milvus.flush();
            }
        } catch (Exception e) {
            log.warn("Milvus flush 失败: {}", e.getMessage());
        }
    }

    @Override
    public VectorStoreInfo info() {
        if (milvus.isReady() || milvus.ensure()) {
            VectorStoreInfo milvusInfo = milvus.info();
            milvusInfo.setDetail(milvusInfo.getDetail() + "；内存备份 " + memory.size() + " 条");
            return milvusInfo;
        }
        VectorStoreInfo mem = memory.info();
        mem.setDetail(milvus.isEnabled()
                ? "Milvus 连不上，已降级到内存向量库。原因：" + milvus.info().getDetail()
                : "Milvus 已按配置关闭（app.milvus.enabled=false），使用内存向量库。");
        return mem;
    }
}
