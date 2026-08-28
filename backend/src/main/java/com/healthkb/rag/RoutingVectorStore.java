package com.healthkb.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存库与 Milvus 之间的路由层：写入双写（内存永远先落一份完整备份），
 * 检索优先走 Milvus 的 HNSW 索引。
 *
 * <p>补账队列：Milvus 故障窗口内的写操作只落了内存，若不补偿，Milvus 恢复后
 * 这批数据在索引里永久缺失——因为只要 Milvus 返回非空命中，检索就不再看内存。
 * 因此写失败时把操作记入有界 FIFO，下次任何入口（search/upsert/info/size）
 * 发现 Milvus 恢复可达就按序重放。删除操作以墓碑形式也进队列，
 * 保证「同文档先插后删」的顺序在重放时不被颠倒；直接删除成功时会把队列里
 * 同文档的旧插入清掉，避免重放把已删文档复活。
 */
@Slf4j
@Primary
@Component
public class RoutingVectorStore implements VectorStore {

    /** 补账队列上限。超出丢最旧并告警：内存里始终有完整备份兜底检索，宁可少补也不错配。 */
    static final int MAX_PENDING = 5_000;

    private final VectorStore memory;
    private final MilvusVectorStore milvus;

    private final ConcurrentLinkedQueue<PendingOp> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final Object replayLock = new Object();

    public RoutingVectorStore(@Qualifier("memoryVectorStore") VectorStore memory, MilvusVectorStore milvus) {
        this.memory = memory;
        this.milvus = milvus;
    }

    @Override
    public void upsert(Long chunkId, Long documentId, float[] vector, String content,
                       String title, String category, String source) {
        replayPending();
        memory.upsert(chunkId, documentId, vector, content, title, category, source);
        boolean synced = false;
        try {
            if (milvus.ensure()) {
                milvus.upsert(chunkId, documentId, vector, content, title, category, source);
                synced = true;
            }
        } catch (Exception e) {
            log.warn("写入 Milvus 失败，已保留内存副本并登记补写: {}", e.getMessage());
        }
        if (!synced) {
            enqueue(new Upsert(chunkId, documentId, vector.clone(), content, title, category, source));
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        memory.deleteByDocumentId(documentId);
        boolean synced = false;
        try {
            if (milvus.isReady() || milvus.ensure()) {
                milvus.deleteByDocumentId(documentId);
                synced = true;
            }
        } catch (Exception e) {
            log.warn("Milvus 删除失败: {}", e.getMessage());
        }
        // 队列里若还压着该文档的旧插入，无论这次删除是否直达 Milvus 都要清掉，
        // 否则恢复后的重放会按旧顺序把它重新插回去（幽灵引用）。
        int purged = purgeDocument(documentId);
        if (!synced || purged > 0) {
            enqueue(new DeleteDoc(documentId));
        }
    }

    @Override
    public List<ScoredChunk> search(float[] query, int topK) {
        replayPending();
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
        replayPending();
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
        pending.clear();
        pendingCount.set(0);
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
    public List<StoredChunk> loadAll() {
        replayPending();
        if (milvus.isReady()) {
            List<StoredChunk> all = milvus.loadAll();
            if (!all.isEmpty()) {
                return all;
            }
        }
        return memory.loadAll();
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
            int waiting = pendingCount.get();
            if (waiting > 0) {
                milvusInfo.setDetail(milvusInfo.getDetail() + "；待补写 " + waiting + " 条");
            }
            return milvusInfo;
        }
        VectorStoreInfo mem = memory.info();
        String reason = milvus.isEnabled()
                ? "Milvus 连不上，已降级到内存向量库。原因：" + milvus.info().getDetail()
                : "Milvus 已按配置关闭（app.milvus.enabled=false），使用内存向量库。";
        int waiting = pendingCount.get();
        mem.setDetail(waiting > 0 ? reason + "；待补写 " + waiting + " 条" : reason);
        return mem;
    }

    /** Milvus 可达时把积压的写操作按序补上。同一时刻只允许一个线程在重放。 */
    private void replayPending() {
        if (pending.isEmpty()) {
            return;
        }
        synchronized (replayLock) {
            int backlog = pendingCount.get();
            while (!pending.isEmpty()) {
                if (!milvus.ensure()) {
                    return;
                }
                PendingOp op = pending.peek();
                try {
                    op.applyTo(milvus);
                    pending.poll();
                    pendingCount.decrementAndGet();
                } catch (Exception e) {
                    log.warn("Milvus 补写失败，保留 {} 条待下次再试: {}",
                            pendingCount.get(), e.getMessage());
                    return;
                }
            }
            if (backlog > 0) {
                log.info("Milvus 已恢复，故障期间积压的 {} 条变更已全部补写", backlog);
            }
        }
    }

    private void enqueue(PendingOp op) {
        pending.add(op);
        int n = pendingCount.incrementAndGet();
        if (n > MAX_PENDING) {
            if (pending.poll() != null) {
                pendingCount.decrementAndGet();
            }
            log.warn("补写队列已满（{} 条），丢弃最旧的一条 —— 检索仍由内存备份兜底", MAX_PENDING);
        }
    }

    /** 移除队列中指定文档的插入操作，返回移除条数。 */
    private int purgeDocument(Long documentId) {
        if (pending.isEmpty() || documentId == null) {
            return 0;
        }
        int removed = 0;
        Iterator<PendingOp> it = pending.iterator();
        while (it.hasNext()) {
            PendingOp op = it.next();
            if (op instanceof Upsert u && documentId.equals(u.documentId())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            pendingCount.addAndGet(-removed);
            log.info("已从补写队列清除文档 {} 的 {} 条过期插入", documentId, removed);
        }
        return removed;
    }

    private sealed interface PendingOp permits Upsert, DeleteDoc {
        void applyTo(MilvusVectorStore target);
    }

    private record Upsert(Long chunkId, Long documentId, float[] vector, String content,
                          String title, String category, String source) implements PendingOp {
        @Override
        public void applyTo(MilvusVectorStore target) {
            target.upsert(chunkId, documentId, vector, content, title, category, source);
        }
    }

    private record DeleteDoc(Long documentId) implements PendingOp {
        @Override
        public void applyTo(MilvusVectorStore target) {
            target.deleteByDocumentId(documentId);
        }
    }
}
