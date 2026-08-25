package com.healthkb.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 医学知识向量化入口。
 *
 * <p>主路径走 Spring AI 的 {@link EmbeddingModel}（见 {@code AiConfig#medicalEmbeddingModel}），
 * 得到真正有语义的向量；未配置 key、断网或调用出错时自动回退 {@link EmbeddingService}
 * 的哈希向量，保证离线也能演示检索。
 *
 * <p>{@link #dimension()} 在启动时按主路径实际返回的维度探测一次 —— Milvus collection
 * 的 schema 依赖它，两者必须一致。
 */
@Slf4j
@Service
public class VectorizeService {

    private static final String PROBE_TEXT = "健康";

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final EmbeddingService fallback;

    private volatile EmbeddingModel model;
    private volatile int dim;
    private final AtomicBoolean degradeLogged = new AtomicBoolean(false);

    public VectorizeService(ObjectProvider<EmbeddingModel> embeddingModelProvider, EmbeddingService fallback) {
        this.embeddingModelProvider = embeddingModelProvider;
        this.fallback = fallback;
        this.dim = fallback.dimension();
    }

    @PostConstruct
    public void probe() {
        EmbeddingModel candidate = embeddingModelProvider.getIfAvailable();
        if (candidate == null) {
            log.warn("医学向量化：未启用真实 embedding 模型，使用哈希兜底向量，维度 {}", dim);
            return;
        }
        try {
            float[] probe = candidate.embed(PROBE_TEXT);
            if (probe == null || probe.length == 0) {
                throw new IllegalStateException("探测返回空向量");
            }
            this.model = candidate;
            this.dim = probe.length;
            log.info("医学向量化：真实 embedding 模型可用，维度 {}", dim);
        } catch (Exception e) {
            log.warn("医学向量化：embedding 模型探测失败（{}），使用哈希兜底向量，维度 {}", e.toString(), dim);
        }
    }

    /** 向量维度。Milvus 建表与内存库都以此为准。 */
    public int dimension() {
        return dim;
    }

    /** 当前是否走的真实语义向量。 */
    public boolean semantic() {
        return model != null;
    }

    public float[] embed(String text) {
        EmbeddingModel current = model;
        if (current != null) {
            try {
                float[] vec = current.embed(text == null ? "" : text);
                if (vec != null && vec.length == dim) {
                    return vec;
                }
                log.warn("embedding 返回维度异常（期望 {}，实际 {}），本次回退哈希向量",
                        dim, vec == null ? 0 : vec.length);
            } catch (Exception e) {
                if (degradeLogged.compareAndSet(false, true)) {
                    log.warn("embedding 调用失败，后续静默回退哈希向量: {}", e.toString());
                }
            }
        }
        return padOrTrim(fallback.embed(text));
    }

    /** 哈希兜底向量维度固定为 app.rag.embedding-dim，真实模型在线时需要对齐到同一维度。 */
    private float[] padOrTrim(float[] vec) {
        if (vec.length == dim) {
            return vec;
        }
        float[] out = new float[dim];
        System.arraycopy(vec, 0, out, 0, Math.min(vec.length, dim));
        return out;
    }
}
