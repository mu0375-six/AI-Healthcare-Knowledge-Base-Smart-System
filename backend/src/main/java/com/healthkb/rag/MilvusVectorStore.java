package com.healthkb.rag;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.DescCollResponseWrapper;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class MilvusVectorStore {

    @Value("${app.milvus.enabled:true}")
    private boolean enabled;

    @Value("${app.milvus.host:localhost}")
    private String host;

    @Value("${app.milvus.port:19530}")
    private int port;

    @Value("${app.milvus.collection:healthkb_chunks}")
    private String collection;

    private final VectorizeService vectorizeService;

    public MilvusVectorStore(@Lazy VectorizeService vectorizeService) {
        this.vectorizeService = vectorizeService;
    }

    /** 向量维度以 VectorizeService 探测到的真实模型维度为准，Milvus schema 必须与之一致。 */
    private int dim() {
        return vectorizeService.dimension();
    }

    private MilvusServiceClient client;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private volatile long lastFailAt;
    private String lastError = "未连接";

    public synchronized boolean ensure() {
        if (!enabled) {
            lastError = "已关闭（app.milvus.enabled=false）";
            ready.set(false);
            return false;
        }
        if (ready.get() && client != null) {
            return true;
        }
        if (lastFailAt > 0 && System.currentTimeMillis() - lastFailAt < 60_000) {
            return false;
        }
        try {
            closeQuietly();
            client = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(4, TimeUnit.SECONDS)
                    .withIdleTimeout(24, TimeUnit.HOURS)
                    .build());
            ensureCollection();
            ready.set(true);
            lastError = "ok";
            log.info("Milvus 已连接 {}:{}/{}", host, port, collection);
            return true;
        } catch (Exception e) {
            ready.set(false);
            lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            lastFailAt = System.currentTimeMillis();
            log.warn("Milvus 不可用，问答改用内存向量库: {}", lastError);
            closeQuietly();
            return false;
        }
    }

    public boolean isReady() {
        return enabled && ready.get();
    }

    /** 是否按配置启用。用于区分「主动关闭」和「连不上」—— 两者都会降级到内存，但对使用者含义不同。 */
    public boolean isEnabled() {
        return enabled;
    }

    public VectorStoreInfo info() {
        int count = 0;
        if (isReady()) {
            try {
                count = size();
            } catch (Exception ignored) {
            }
        }
        return new VectorStoreInfo(
                "milvus",
                isReady(),
                count,
                dim(),
                collection,
                isReady() ? "HNSW / COSINE" : lastError);
    }

    public void upsert(Long chunkId, Long documentId, float[] vector, String content,
                       String title, String category, String source) {
        if (!ensure()) {
            return;
        }
        List<InsertParam.Field> fields = List.of(
                new InsertParam.Field("chunk_id", List.of(chunkId)),
                new InsertParam.Field("document_id", List.of(documentId)),
                new InsertParam.Field("embedding", List.of(toList(vector))),
                new InsertParam.Field("content", List.of(clip(content, 1800))),
                new InsertParam.Field("title", List.of(clip(title, 240))),
                new InsertParam.Field("category", List.of(clip(category, 60))),
                new InsertParam.Field("source", List.of(clip(source, 480)))
        );
        R<MutationResult> resp = client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
        failIf(resp, "insert");
    }

    public void flush() {
        if (!isReady() || client == null) {
            return;
        }
        // 重试要短：flush 走启动/管理线程，长退避会把调用方挂住半分钟以上
        RuntimeException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                R<?> resp = client.flush(FlushParam.newBuilder().addCollectionName(collection).build());
                if (resp.getStatus() == R.Status.Success.getCode()) {
                    return;
                }
                last = new IllegalStateException("flush: " + resp.getMessage());
            } catch (RuntimeException e) {
                last = e;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(400L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (last != null) {
            log.warn("Milvus flush 跳过: {}", last.getMessage());
        }
    }

    public void deleteByDocumentId(Long documentId) {
        if (!ensure() || documentId == null) {
            return;
        }
        R<MutationResult> resp = client.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("document_id == " + documentId)
                .build());
        failIf(resp, "delete");
    }

    public List<ScoredChunk> search(float[] query, int topK) {
        if (!ensure() || query == null || topK <= 0) {
            return List.of();
        }
        R<SearchResults> resp = client.search(SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.COSINE)
                .withTopK(topK)
                .withFloatVectors(List.of(toList(query)))
                .withVectorFieldName("embedding")
                .withOutFields(List.of("chunk_id", "document_id", "content", "title", "category", "source"))
                .withParams("{\"ef\":16}")
                .build());
        failIf(resp, "search");
        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<ScoredChunk> out = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            SearchResultsWrapper.IDScore idScore = scores.get(i);
            out.add(new ScoredChunk(
                    asLong(wrapper.getFieldData("chunk_id", 0).get(i)),
                    asLong(wrapper.getFieldData("document_id", 0).get(i)),
                    String.valueOf(wrapper.getFieldData("content", 0).get(i)),
                    String.valueOf(wrapper.getFieldData("title", 0).get(i)),
                    String.valueOf(wrapper.getFieldData("category", 0).get(i)),
                    String.valueOf(wrapper.getFieldData("source", 0).get(i)),
                    idScore.getScore()));
        }
        return out;
    }

    public int size() {
        if (!ensure()) {
            return 0;
        }
        R<QueryResults> resp = client.query(QueryParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("chunk_id >= 0")
                .withOutFields(List.of("chunk_id"))
                .build());
        if (resp.getStatus() != R.Status.Success.getCode()) {
            return 0;
        }
        QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
        return (int) Math.min(wrapper.getRowCount(), Integer.MAX_VALUE);
    }

    /**
     * 全量读回（含向量），供启动时把 Milvus 里已算好的向量回灌内存库 ——
     * 替代「重跑一遍 embedding」的重建路径。Milvus 不可达或查询失败返回空表，
     * 由调用方兜底为全量重建。
     */
    public List<StoredChunk> loadAll() {
        if (!ensure()) {
            return List.of();
        }
        R<QueryResults> resp = client.query(QueryParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("chunk_id >= 0")
                .withOutFields(List.of("chunk_id", "document_id", "embedding",
                        "content", "title", "category", "source"))
                .build());
        if (resp.getStatus() != R.Status.Success.getCode()) {
            log.warn("Milvus loadAll 失败: {}", resp.getMessage());
            return List.of();
        }
        QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
        List<StoredChunk> out = new ArrayList<>();
        for (QueryResultsWrapper.RowRecord row : wrapper.getRowRecords()) {
            float[] vec;
            try {
                vec = toFloatArray(row.get("embedding"));
            } catch (Exception e) {
                log.warn("loadAll 解析向量失败，该行跳过: {}", e.toString());
                continue;
            }
            out.add(new StoredChunk(
                    asLong(row.get("chunk_id")),
                    asLong(row.get("document_id")),
                    vec,
                    String.valueOf(row.get("content")),
                    String.valueOf(row.get("title")),
                    String.valueOf(row.get("category")),
                    String.valueOf(row.get("source"))));
        }
        return out;
    }

    private static float[] toFloatArray(Object raw) {
        if (raw instanceof float[] f) {
            return f;
        }
        if (raw instanceof List<?> list) {
            float[] v = new float[list.size()];
            for (int i = 0; i < v.length; i++) {
                Object e = list.get(i);
                v[i] = e instanceof Number num ? num.floatValue() : (float) Double.parseDouble(String.valueOf(e));
            }
            return v;
        }
        throw new IllegalStateException("不支持的向量数据类型: " + (raw == null ? "null" : raw.getClass().getName()));
    }

    public void clear() {
        if (!ensure()) {
            return;
        }
        client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(collection).build());
        ready.set(false);
        ensureCollection();
        ready.set(true);
    }

    private void ensureCollection() {
        R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collection).build());
        failIf(has, "hasCollection");
        if (Boolean.TRUE.equals(has.getData())) {
            int existing = existingDimension();
            if (existing > 0 && existing != dim()) {
                // 换过 embedding 模型：旧向量维度对不上，只能重建 collection，
                // 之后由 RagService#rebuildFromDatabase 从库里重新灌数据。
                log.warn("Milvus collection {} 维度 {} 与当前模型维度 {} 不符，重建集合",
                        collection, existing, dim());
                failIf(client.dropCollection(DropCollectionParam.newBuilder()
                        .withCollectionName(collection).build()), "dropCollection");
            } else {
                failIf(client.loadCollection(LoadCollectionParam.newBuilder()
                        .withCollectionName(collection).build()), "load");
                return;
            }
        }
        FieldType id = FieldType.newBuilder()
                .withName("chunk_id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        FieldType doc = FieldType.newBuilder().withName("document_id").withDataType(DataType.Int64).build();
        FieldType vec = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(dim())
                .build();
        FieldType content = FieldType.newBuilder().withName("content").withDataType(DataType.VarChar).withMaxLength(2048).build();
        FieldType title = FieldType.newBuilder().withName("title").withDataType(DataType.VarChar).withMaxLength(256).build();
        FieldType category = FieldType.newBuilder().withName("category").withDataType(DataType.VarChar).withMaxLength(64).build();
        FieldType source = FieldType.newBuilder().withName("source").withDataType(DataType.VarChar).withMaxLength(512).build();
        failIf(client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("康识医学知识切片向量")
                .withShardsNum(1)
                .addFieldType(id)
                .addFieldType(doc)
                .addFieldType(vec)
                .addFieldType(content)
                .addFieldType(title)
                .addFieldType(category)
                .addFieldType(source)
                .build()), "createCollection");
        failIf(client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName("embedding")
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\":8,\"efConstruction\":64}")
                .build()), "createIndex");
        failIf(client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build()), "load");
        client.flush(FlushParam.newBuilder().addCollectionName(collection).build());
    }

    /** 读取已存在 collection 的 embedding 字段维度；读不到返回 0（当作无需重建）。 */
    private int existingDimension() {
        try {
            var resp = client.describeCollection(DescribeCollectionParam.newBuilder()
                    .withCollectionName(collection).build());
            if (resp == null || resp.getStatus() != R.Status.Success.getCode()) {
                return 0;
            }
            DescCollResponseWrapper wrapper = new DescCollResponseWrapper(resp.getData());
            var field = wrapper.getFieldByName("embedding");
            return field == null ? 0 : field.getDimension();
        } catch (Exception e) {
            log.warn("读取 Milvus collection 维度失败: {}", e.toString());
            return 0;
        }
    }

    private void failIf(R<?> resp, String op) {
        if (resp == null || resp.getStatus() != R.Status.Success.getCode()) {
            String msg = resp == null ? "null" : String.valueOf(resp.getMessage());
            throw new IllegalStateException("Milvus " + op + " 失败: " + msg);
        }
    }

    private static List<Float> toList(float[] v) {
        List<Float> list = new ArrayList<>(v.length);
        for (float x : v) {
            list.add(x);
        }
        return list;
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return o == null ? 0L : Long.parseLong(String.valueOf(o));
    }

    private static String clip(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n);
    }

    private void closeQuietly() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
            client = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        closeQuietly();
    }
}
