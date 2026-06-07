package com.langgraph4j.engine.rag;

import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.repository.KnowledgeBaseRepository;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;

/**
 * 多知识库管理器
 * 
 * 架构：
 * - 全局唯一 Milvus Collection: kb_chunks，使用 knowledge_base_id 作为 Partition Key
 * - 每个知识库对应一个 MilvusKnowledgeBase 实例，共享同一个 MilvusClientV2
 * - 维护知识库实例缓存，配置变更时自动失效
 * - 统一管理 Milvus 连接和 Collection 生命周期
 */
@Slf4j
public class KnowledgeBaseManager {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeBaseRepository repository;
    private final RerankerClient rerankerClient;
    private final int embeddingDimensions;
    private final boolean enableBM25;
    private final boolean enableReranker;

    private static final String COLLECTION_NAME = "kb_chunks";

    // 知识库实例缓存
    private final ConcurrentHashMap<String, KnowledgeBase> knowledgeBaseCache = new ConcurrentHashMap<>();

    // 文档索引线程池
    private final ExecutorService indexExecutor;

    public KnowledgeBaseManager(
            String milvusHost, int milvusPort,
            String milvusToken, String dbName,
            EmbeddingClient embeddingClient,
            KnowledgeBaseRepository repository,
            RerankerClient rerankerClient,
            int embeddingDimensions,
            boolean enableBM25,
            boolean enableReranker) {

        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.rerankerClient = rerankerClient;
        this.embeddingDimensions = embeddingDimensions;
        this.enableBM25 = enableBM25;
        this.enableReranker = enableReranker;

        // 索引线程池：有界队列，防止 OOM
        this.indexExecutor = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 连接 Milvus（如果已配置）
        if (milvusHost != null && !milvusHost.isEmpty()) {
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri("http://" + milvusHost + ":" + milvusPort)
                    .connectTimeoutMs(30000L)
                    .rpcDeadlineMs(60000L)
                    .build();
            log.info("Connecting to Milvus at {}:{} ...", milvusHost, milvusPort);
            this.milvusClient = new MilvusClientV2(connectConfig);
            initializeGlobalCollection();
            log.info("KnowledgeBaseManager initialized, collection: {}", COLLECTION_NAME);
        } else {
            this.milvusClient = null;
            log.info("KnowledgeBaseManager initialized (CRUD-only mode, no Milvus)");
        }
    }

    /**
     * 初始化全局 Milvus Collection（单 Collection + Partition Key）
     */
    private void initializeGlobalCollection() {
        try {
            Boolean exists = milvusClient.hasCollection(
                    HasCollectionReq.builder().collectionName(COLLECTION_NAME).build());
            if (exists != null && exists) {
                log.info("Collection {} already exists", COLLECTION_NAME);
                return;
            }

            CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();

            // 主键
            schema.addField(AddFieldReq.builder()
                    .fieldName("chunk_id")
                    .dataType(DataType.VarChar)
                    .maxLength(128)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());

            // 知识库 ID（Partition Key）
            schema.addField(AddFieldReq.builder()
                    .fieldName("knowledge_base_id")
                    .dataType(DataType.VarChar)
                    .maxLength(64)
                    .isPartitionKey(true)
                    .build());

            // 文档 ID
            schema.addField(AddFieldReq.builder()
                    .fieldName("document_id")
                    .dataType(DataType.VarChar)
                    .maxLength(128)
                    .build());

            // 分块内容
            schema.addField(AddFieldReq.builder()
                    .fieldName("content")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());

            // 分块索引
            schema.addField(AddFieldReq.builder()
                    .fieldName("chunk_index")
                    .dataType(DataType.Int32)
                    .build());

            // Dense Vector (Embedding)
            schema.addField(AddFieldReq.builder()
                    .fieldName("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(embeddingDimensions)
                    .build());

            // Sparse Vector (BM25)
            if (enableBM25) {
                schema.addField(AddFieldReq.builder()
                        .fieldName("sparse_embedding")
                        .dataType(DataType.SparseFloatVector)
                        .build());
            }

            // 索引参数
            List<IndexParam> indexParams = new ArrayList<>();

            Map<String, Object> hnswParams = new HashMap<>();
            hnswParams.put("M", 16);
            hnswParams.put("efConstruction", 256);
            indexParams.add(IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(hnswParams)
                    .build());

            if (enableBM25) {
                Map<String, Object> sparseParams = new HashMap<>();
                sparseParams.put("drop_ratio_build", 0.2);
                indexParams.add(IndexParam.builder()
                        .fieldName("sparse_embedding")
                        .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                        .metricType(IndexParam.MetricType.IP)
                        .extraParams(sparseParams)
                        .build());
            }

            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .collectionSchema(schema)
                    .indexParams(indexParams)
                    .build();
            milvusClient.createCollection(createReq);

            log.info("Global Milvus collection created: {}", COLLECTION_NAME);
        } catch (Exception e) {
            log.error("Failed to initialize global Milvus collection", e);
            throw new RuntimeException("Failed to initialize global Milvus collection", e);
        }
    }

    /**
     * 获取或创建知识库实例
     */
    public KnowledgeBase getKnowledgeBase(String kbId) {
        // Milvus 不可用时，不创建 KnowledgeBase 实例（仅支持 CRUD）
        if (milvusClient == null) {
            return null;
        }
        KnowledgeBase kb = knowledgeBaseCache.get(kbId);
        if (kb != null) {
            return kb;
        }
        Map<String, Object> kbConfig = repository.getKnowledgeBase(kbId);
        if (kbConfig == null) {
            log.warn("Knowledge base not found: {}", kbId);
            return null;
        }
        kb = createKnowledgeBaseInstance(kbId, kbConfig);
        knowledgeBaseCache.put(kbId, kb);
        return kb;
    }

    /**
     * 创建知识库实例并注册到缓存
     */
    public KnowledgeBase createKnowledgeBase(String kbId, Map<String, Object> kbConfig) {
        repository.insertKnowledgeBase(kbConfig);
        KnowledgeBase kb = createKnowledgeBaseInstance(kbId, kbConfig);
        knowledgeBaseCache.put(kbId, kb);
        return kb;
    }

    /**
     * 删除知识库
     */
    public void deleteKnowledgeBase(String kbId) {
        // 1. 删除 Milvus 中的向量数据（如果有 Milvus）
        if (milvusClient != null) {
            try {
                milvusClient.delete(io.milvus.v2.service.vector.request.DeleteReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .filter("knowledge_base_id == \"" + kbId + "\"")
                        .build());
            } catch (Exception e) {
                log.error("Failed to delete vectors for knowledge base: {}", kbId, e);
            }
        }

        // 2. 删除 MySQL 中的元数据（级联删除文档和分块）
        repository.deleteKnowledgeBase(kbId);

        // 3. 清除缓存
        knowledgeBaseCache.remove(kbId);
        log.info("Knowledge base deleted: {}", kbId);
    }

    /**
     * 使知识库缓存失效（配置变更后调用）
     */
    public void invalidateCache(String kbId) {
        knowledgeBaseCache.remove(kbId);
        log.info("Knowledge base cache invalidated: {}", kbId);
    }

    /**
     * 更新知识库配置
     */
    public void updateKnowledgeBase(String kbId, Map<String, Object> updates) {
        repository.updateKnowledgeBase(kbId, updates);
        invalidateCache(kbId);
    }

    /**
     * 创建 KnowledgeBase 实例
     */
    private KnowledgeBase createKnowledgeBaseInstance(String kbId, Map<String, Object> kbConfig) {
        KnowledgeBase.RetrievalConfig retrievalConfig = KnowledgeBase.RetrievalConfig.builder()
                .topK((Integer) kbConfig.getOrDefault("topK", 5))
                .scoreThreshold(((Number) kbConfig.getOrDefault("scoreThreshold", 0.7f)).floatValue())
                .searchType((String) kbConfig.getOrDefault("searchType", "hybrid"))
                .build();

        boolean kbEnableBM25 = (Boolean) kbConfig.getOrDefault("enableBM25", enableBM25);
        boolean kbEnableReranker = (Boolean) kbConfig.getOrDefault("enableReranker", enableReranker);

        return new MilvusKnowledgeBase(
                kbId,
                (String) kbConfig.get("name"),
                milvusClient,
                COLLECTION_NAME,
                embeddingClient,
                retrievalConfig,
                embeddingDimensions,
                kbEnableReranker ? rerankerClient : null,
                kbEnableBM25,
                kbEnableReranker,
                indexExecutor,
                repository
        );
    }

    /**
     * 列出所有知识库
     */
    public List<Map<String, Object>> listKnowledgeBases() {
        return repository.listKnowledgeBases();
    }

    /**
     * 获取知识库配置
     */
    public Map<String, Object> getKnowledgeBaseConfig(String kbId) {
        return repository.getKnowledgeBase(kbId);
    }

    /**
     * 获取文档列表
     */
    public List<Map<String, Object>> listDocuments(String kbId, int offset, int limit) {
        return repository.listDocuments(kbId, offset, limit);
    }

    /**
     * 获取文档详情
     */
    public Map<String, Object> getDocument(String docId) {
        return repository.getDocument(docId);
    }

    /**
     * 获取分块列表
     */
    public List<Map<String, Object>> listChunks(String docId) {
        return repository.listChunks(docId);
    }

    public MilvusClientV2 getMilvusClient() {
        return milvusClient;
    }

    public String getCollectionName() {
        return COLLECTION_NAME;
    }

    public ExecutorService getIndexExecutor() {
        return indexExecutor;
    }

    public KnowledgeBaseRepository getRepository() {
        return repository;
    }

    /**
     * 检查 Milvus 连接状态
     */
    public boolean isMilvusAvailable() {
        try {
            milvusClient.hasCollection(
                    HasCollectionReq.builder().collectionName(COLLECTION_NAME).build());
            return true;
        } catch (Exception e) {
            log.error("Milvus connection check failed", e);
            return false;
        }
    }

    public void close() {
        indexExecutor.shutdown();
        try {
            indexExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        milvusClient.close();
        repository.close();
    }
}