package com.langgraph4j.engine.rag;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.repository.KnowledgeBaseRepository;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Milvus 知识库实现
 * 单 Collection 模式：所有知识库共享同一个 kb_chunks Collection
 * 通过 knowledge_base_id 字段（Partition Key）隔离不同知识库
 *
 * 检索能力：
 * 1. Embedding 向量检索 (HNSW + COSINE)
 * 2. BM25 稀疏检索 (SPARSE_INVERTED_INDEX + IP)
 * 3. 混合检索：Dense + Sparse 并行检索，RRF 融合
 * 4. Reranker 重排序（可选）
 */
@Slf4j
public class MilvusKnowledgeBase extends KnowledgeBase {

    private final MilvusClientV2 milvusClient;
    private final String collectionName;
    private final int embeddingDimensions;
    private final RerankerClient rerankerClient;
    private final boolean enableBM25;
    private final boolean enableReranker;
    private final ExecutorService executorService;
    private final KnowledgeBaseRepository repository;
    private final Gson gson;

    /**
     * 共享 MilvusClient 模式（多知识库）
     */
    public MilvusKnowledgeBase(String id, String name,
                                MilvusClientV2 milvusClient,
                                String collectionName,
                                EmbeddingClient embeddingClient,
                                RetrievalConfig config,
                                int embeddingDimensions,
                                RerankerClient rerankerClient,
                                boolean enableBM25,
                                boolean enableReranker,
                                ExecutorService executorService,
                                KnowledgeBaseRepository repository) {
        super(id, name, embeddingClient, config);
        this.milvusClient = milvusClient;
        this.collectionName = collectionName;
        this.embeddingDimensions = embeddingDimensions;
        this.rerankerClient = rerankerClient;
        this.enableBM25 = enableBM25;
        this.enableReranker = enableReranker;
        this.executorService = executorService;
        this.repository = repository;
        this.gson = new Gson();

        log.info("Milvus knowledge base created: id={}, collection={}, BM25={}, Reranker={}",
                id, collectionName, enableBM25, enableReranker);
    }

    /**
     * 独立 MilvusClient 模式（单知识库，向后兼容）
     */
    public MilvusKnowledgeBase(String id, String name,
                                String milvusHost, int milvusPort,
                                String milvusToken, String dbName,
                                EmbeddingClient embeddingClient,
                                RetrievalConfig config,
                                int embeddingDimensions,
                                RerankerClient rerankerClient,
                                boolean enableBM25,
                                boolean enableReranker) {
        super(id, name, embeddingClient, config);
        this.collectionName = "kb_" + id.replace("-", "_");
        this.embeddingDimensions = embeddingDimensions;
        this.rerankerClient = rerankerClient;
        this.enableBM25 = enableBM25;
        this.enableReranker = enableReranker;
        this.executorService = java.util.concurrent.Executors.newFixedThreadPool(10);
        this.repository = null;
        this.gson = new Gson();

        io.milvus.v2.client.ConnectConfig connectConfig = io.milvus.v2.client.ConnectConfig.builder()
                .uri("http://" + milvusHost + ":" + milvusPort)
                .build();
        this.milvusClient = new MilvusClientV2(connectConfig);

        initializeCollection();
        log.info("Milvus knowledge base (standalone) initialized: {}, collection: {}", id, collectionName);
    }

    private void initializeCollection() {
        try {
            Boolean exists = milvusClient.hasCollection(
                    io.milvus.v2.service.collection.request.HasCollectionReq.builder()
                            .collectionName(collectionName).build());
            if (exists != null && exists) {
                log.info("Collection {} already exists", collectionName);
                return;
            }

            io.milvus.v2.service.collection.request.CreateCollectionReq.CollectionSchema schema =
                    milvusClient.createSchema();

            schema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder()
                    .fieldName("chunk_id")
                    .dataType(io.milvus.v2.common.DataType.VarChar)
                    .maxLength(128)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());

            schema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder()
                    .fieldName("document_id")
                    .dataType(io.milvus.v2.common.DataType.VarChar)
                    .maxLength(128)
                    .build());

            schema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder()
                    .fieldName("content")
                    .dataType(io.milvus.v2.common.DataType.VarChar)
                    .maxLength(65535)
                    .build());

            schema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder()
                    .fieldName("chunk_index")
                    .dataType(io.milvus.v2.common.DataType.Int32)
                    .build());

            schema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder()
                    .fieldName("embedding")
                    .dataType(io.milvus.v2.common.DataType.FloatVector)
                    .dimension(embeddingDimensions)
                    .build());

            if (enableBM25) {
                schema.addField(io.milvus.v2.service.collection.request.AddFieldReq.builder()
                        .fieldName("sparse_embedding")
                        .dataType(io.milvus.v2.common.DataType.SparseFloatVector)
                        .build());
            }

            List<io.milvus.v2.common.IndexParam> indexParams = new ArrayList<>();

            Map<String, Object> hnswParams = new HashMap<>();
            hnswParams.put("M", 16);
            hnswParams.put("efConstruction", 256);
            indexParams.add(io.milvus.v2.common.IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(io.milvus.v2.common.IndexParam.IndexType.HNSW)
                    .metricType(io.milvus.v2.common.IndexParam.MetricType.COSINE)
                    .extraParams(hnswParams)
                    .build());

            if (enableBM25) {
                Map<String, Object> sparseParams = new HashMap<>();
                sparseParams.put("drop_ratio_build", 0.2);
                indexParams.add(io.milvus.v2.common.IndexParam.builder()
                        .fieldName("sparse_embedding")
                        .indexType(io.milvus.v2.common.IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                        .metricType(io.milvus.v2.common.IndexParam.MetricType.IP)
                        .extraParams(sparseParams)
                        .build());
            }

            io.milvus.v2.service.collection.request.CreateCollectionReq createReq =
                    io.milvus.v2.service.collection.request.CreateCollectionReq.builder()
                            .collectionName(collectionName)
                            .collectionSchema(schema)
                            .indexParams(indexParams)
                            .build();
            milvusClient.createCollection(createReq);

            log.info("Milvus collection created: {}", collectionName);
        } catch (Exception e) {
            log.error("Failed to initialize Milvus collection", e);
            throw new RuntimeException("Failed to initialize Milvus collection", e);
        }
    }

    /**
     * 构建 knowledge_base_id 过滤表达式
     */
    private String kbFilter() {
        return "knowledge_base_id == \"" + id + "\"";
    }

    @Override
    public CompletableFuture<Void> addDocument(Document document) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<String> chunks = splitText(document.getContent(), config.getChunkConfig());
                List<float[]> embeddings = embeddingClient.embedDocuments(chunks).join();

                List<JsonObject> rows = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    String chunkId = UUID.randomUUID().toString();
                    JsonObject row = new JsonObject();
                    row.addProperty("chunk_id", chunkId);
                    row.addProperty("knowledge_base_id", id);
                    row.addProperty("document_id", document.getId());
                    row.addProperty("content", chunks.get(i));
                    row.addProperty("chunk_index", i);
                    row.add("embedding", toJsonArray(embeddings.get(i)));

                    if (enableBM25) {
                        SortedMap<Long, Float> sparseMap = Bm25Util.tokenizeToSparseMap(chunks.get(i));
                        row.add("sparse_embedding", gson.toJsonTree(sparseMap));
                    }

                    rows.add(row);
                }

                InsertReq insertReq = InsertReq.builder()
                        .collectionName(collectionName)
                        .data(rows)
                        .build();
                milvusClient.insert(insertReq);

                log.debug("Document added to Milvus: {}, chunks: {}", document.getId(), chunks.size());
            } catch (Exception e) {
                log.error("Failed to add document to Milvus", e);
                throw new RuntimeException("Failed to add document to Milvus", e);
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> addDocuments(List<Document> documents) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Document doc : documents) {
            futures.add(addDocument(doc));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> null);
    }

    @Override
    public CompletableFuture<Void> deleteDocument(String documentId) {
        return CompletableFuture.runAsync(() -> {
            try {
                String filter = kbFilter() + " && document_id == \"" + documentId + "\"";
                DeleteReq deleteReq = DeleteReq.builder()
                        .collectionName(collectionName)
                        .filter(filter)
                        .build();
                milvusClient.delete(deleteReq);
                log.debug("Document deleted from Milvus: {}", documentId);
            } catch (Exception e) {
                log.error("Failed to delete document from Milvus", e);
                throw new RuntimeException("Failed to delete document from Milvus", e);
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<List<DocumentChunk>> retrieve(String query, RetrievalConfig retrievalConfig) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String searchType = retrievalConfig.getSearchType();
                int topK = retrievalConfig.getTopK();

                List<DocumentChunk> results;
                switch (searchType) {
                    case "hybrid":
                        results = hybridSearch(query, null, topK).join();
                        break;
                    case "bm25":
                        results = bm25Search(query, topK).join();
                        break;
                    case "similarity":
                    default:
                        float[] queryEmbedding = embeddingClient.embedQuery(query).join();
                        results = similaritySearch(queryEmbedding, topK).join();
                        break;
                }

                if (enableReranker && rerankerClient != null && !results.isEmpty()) {
                    results = rerankerClient.rerank(query, results, topK).join();
                }

                float threshold = retrievalConfig.getScoreThreshold();
                results = results.stream()
                        .filter(chunk -> chunk.getScore() >= threshold)
                        .toList();

                return results;
            } catch (Exception e) {
                log.error("Failed to retrieve from Milvus", e);
                return new ArrayList<>();
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<List<DocumentChunk>> similaritySearch(float[] embedding, int topK) {
        // 同步执行，避免线程池死锁（外层 retrieve() 已在 executorService 中运行）
        try {
            List<String> outputFields = List.of("chunk_id", "document_id", "content", "chunk_index");

            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("ef", 128);

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(new FloatVec(embedding)))
                    .annsField("embedding")
                    .topK(topK)
                    .outputFields(outputFields)
                    .filter(kbFilter())
                    .searchParams(searchParams)
                    .build();

            SearchResp searchResp = milvusClient.search(searchReq);
            return CompletableFuture.completedFuture(parseSearchResults(searchResp));
        } catch (Exception e) {
            log.error("Failed to similarity search in Milvus", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    public CompletableFuture<List<DocumentChunk>> bm25Search(String query, int topK) {
        if (!enableBM25) {
            log.warn("BM25 is not enabled, falling back to similarity search");
            float[] queryEmbedding = embeddingClient.embedQuery(query).join();
            return similaritySearch(queryEmbedding, topK);
        }

        // 同步执行，避免线程池死锁
        try {
            List<String> outputFields = List.of("chunk_id", "document_id", "content", "chunk_index");

            SparseFloatVec querySparseVec = new SparseFloatVec(Bm25Util.tokenizeToSparseMap(query));

            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("drop_ratio_search", 0.2);

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(querySparseVec))
                    .annsField("sparse_embedding")
                    .topK(topK)
                    .outputFields(outputFields)
                    .filter(kbFilter())
                    .searchParams(searchParams)
                    .build();

            SearchResp searchResp = milvusClient.search(searchReq);
            return CompletableFuture.completedFuture(parseSearchResults(searchResp));
        } catch (Exception e) {
            log.error("Failed to BM25 search in Milvus", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    @Override
    public CompletableFuture<List<DocumentChunk>> hybridSearch(String query, float[] embedding, int topK) {
        if (!enableBM25) {
            log.warn("BM25 is not enabled, falling back to similarity search");
            float[] queryEmbedding = embedding != null ? embedding :
                    embeddingClient.embedQuery(query).join();
            return similaritySearch(queryEmbedding, topK);
        }

        // 同步执行，避免线程池死锁
        try {
            float[] queryEmbedding = embedding != null ? embedding :
                    embeddingClient.embedQuery(query).join();

            List<DocumentChunk> denseResults = similaritySearch(queryEmbedding, topK * 2).join();
            List<DocumentChunk> sparseResults = bm25Search(query, topK * 2).join();

            return CompletableFuture.completedFuture(reciprocalRankFusion(denseResults, sparseResults, topK));
        } catch (Exception e) {
            log.error("Failed to hybrid search in Milvus", e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    private List<DocumentChunk> reciprocalRankFusion(List<DocumentChunk> denseResults,
                                                      List<DocumentChunk> sparseResults,
                                                      int topK) {
        int k = 60;
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, DocumentChunk> chunkMap = new HashMap<>();

        for (int i = 0; i < denseResults.size(); i++) {
            DocumentChunk chunk = denseResults.get(i);
            String key = chunk.getDocumentId() + "_" + chunk.getChunkIndex();
            scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
            chunkMap.putIfAbsent(key, chunk);
        }

        for (int i = 0; i < sparseResults.size(); i++) {
            DocumentChunk chunk = sparseResults.get(i);
            String key = chunk.getDocumentId() + "_" + chunk.getChunkIndex();
            scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
            chunkMap.putIfAbsent(key, chunk);
        }

        // 归一化 RRF 分数到 [0, 1] 范围
        // 最大可能分数: 排名第一的结果在两个列表中都排第一 → 2/(k+1)
        double maxPossibleScore = 2.0 / (k + 1);

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    DocumentChunk chunk = chunkMap.get(entry.getKey());
                    double normalizedScore = entry.getValue() / maxPossibleScore;
                    chunk.setScore((float) Math.min(1.0, normalizedScore));
                    return chunk;
                })
                .toList();
    }

    private List<DocumentChunk> parseSearchResults(SearchResp searchResp) {
        List<DocumentChunk> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();

        for (List<SearchResp.SearchResult> resultList : searchResults) {
            for (SearchResp.SearchResult result : resultList) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .id(String.valueOf(result.getId()))
                        .documentId(String.valueOf(result.getEntity().get("document_id")))
                        .content(String.valueOf(result.getEntity().get("content")))
                        .chunkIndex((Integer) result.getEntity().get("chunk_index"))
                        .score(result.getScore())
                        .build();
                results.add(chunk);
            }
        }
        return results;
    }

    /**
     * 删除知识库的所有向量数据
     */
    public CompletableFuture<Void> deleteAllVectors() {
        return CompletableFuture.runAsync(() -> {
            try {
                DeleteReq deleteReq = DeleteReq.builder()
                        .collectionName(collectionName)
                        .filter(kbFilter())
                        .build();
                milvusClient.delete(deleteReq);
                log.info("All vectors deleted for knowledge base: {}", id);
            } catch (Exception e) {
                log.error("Failed to delete all vectors for knowledge base: {}", id, e);
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<List<Document>> listDocuments(int offset, int limit) {
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    @Override
    public CompletableFuture<Long> getDocumentCount() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                io.milvus.v2.service.collection.request.GetCollectionStatsReq statsReq =
                        io.milvus.v2.service.collection.request.GetCollectionStatsReq.builder()
                                .collectionName(collectionName)
                                .build();
                io.milvus.v2.service.collection.response.GetCollectionStatsResp statsResp =
                        milvusClient.getCollectionStats(statsReq);
                return statsResp.getNumOfEntities();
            } catch (Exception e) {
                log.error("Failed to get document count from Milvus", e);
                return 0L;
            }
        }, executorService);
    }

    @Override
    public CompletableFuture<Void> clear() {
        return deleteAllVectors();
    }

    private com.google.gson.JsonArray toJsonArray(float[] arr) {
        com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
        for (float v : arr) {
            jsonArray.add(v);
        }
        return jsonArray;
    }
}