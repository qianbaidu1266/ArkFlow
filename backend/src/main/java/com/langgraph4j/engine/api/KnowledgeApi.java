package com.langgraph4j.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.langgraph4j.engine.rag.KnowledgeBase;
import com.langgraph4j.engine.rag.KnowledgeBaseManager;
import com.langgraph4j.engine.rag.DocumentIndexService;
import com.langgraph4j.engine.repository.KnowledgeBaseRepository;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.FileUpload;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 知识库 API 路由器
 * 挂载到 WorkflowApi 的 Router 上，共享端口
 */
@Slf4j
public class KnowledgeApi {

    private final KnowledgeBaseManager kbManager;
    private final KnowledgeBaseRepository repository;
    private final DocumentIndexService indexService;
    private final ObjectMapper objectMapper;

    public KnowledgeApi(KnowledgeBaseManager kbManager) {
        this.kbManager = kbManager;
        this.repository = kbManager.getRepository();
        this.objectMapper = new ObjectMapper();
        this.indexService = new DocumentIndexService(kbManager,
                System.getProperty("user.dir") + "/data/uploads");
    }
    
    /**
     * 无 Milvus 的构造器，仅支持 MySQL CRUD，不支持向量检索和文档索引
     */
    public KnowledgeApi(KnowledgeBaseRepository repository) {
        this.kbManager = null;
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.indexService = null;
    }

    /**
     * 挂载知识库路由到现有 Router
     */
    public void mountRoutes(Router router) {
        // 知识库 CRUD
        router.get("/api/knowledge-bases").handler(this::listKnowledgeBases);
        router.get("/api/knowledge-bases/:id").handler(this::getKnowledgeBase);
        router.post("/api/knowledge-bases").handler(this::createKnowledgeBase);
        router.put("/api/knowledge-bases/:id").handler(this::updateKnowledgeBase);
        router.delete("/api/knowledge-bases/:id").handler(this::deleteKnowledgeBase);

        // 文档管理
        router.get("/api/knowledge-bases/:id/documents").handler(this::listDocuments);
        router.get("/api/knowledge-bases/:id/documents/:docId").handler(this::getDocument);
        router.post("/api/knowledge-bases/:id/documents").handler(this::uploadDocument);
        router.delete("/api/knowledge-bases/:id/documents/:docId").handler(this::deleteDocument);

        // 分块管理
        router.get("/api/knowledge-bases/:id/documents/:docId/chunks").handler(this::listChunks);
        router.put("/api/knowledge-bases/:id/documents/:docId/chunks/:chunkId").handler(this::updateChunk);

        // 检索
        router.post("/api/knowledge-bases/:id/search").handler(this::search);

        log.info("Knowledge API routes mounted");
    }

    // ========== 知识库 CRUD ==========

    private void listKnowledgeBases(RoutingContext ctx) {
        try {
            List<Map<String, Object>> list = kbManager.listKnowledgeBases();
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(list));
        } catch (Exception e) {
            log.error("Failed to list knowledge bases", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void getKnowledgeBase(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            Map<String, Object> kb = kbManager.getKnowledgeBaseConfig(id);
            if (kb == null) {
                ctx.response().setStatusCode(404).end("{\"error\":\"Knowledge base not found\"}");
                return;
            }
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(kb));
        } catch (Exception e) {
            log.error("Failed to get knowledge base", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void createKnowledgeBase(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"error\":\"Request body is required\"}");
                return;
            }

            String name = body.getString("name");
            if (name == null || name.isBlank()) {
                ctx.response().setStatusCode(400).end("{\"error\":\"name is required\"}");
                return;
            }

            String kbId = UUID.randomUUID().toString();

            Map<String, Object> kbConfig = new LinkedHashMap<>();
            kbConfig.put("id", kbId);
            kbConfig.put("name", name);
            kbConfig.put("description", body.getString("description", ""));
            kbConfig.put("embeddingModel", body.getString("embeddingModel", ""));
            kbConfig.put("embeddingDimensions", body.getInteger("embeddingDimensions", 1024));
            kbConfig.put("searchType", body.getString("searchType", "hybrid"));
            kbConfig.put("enableBM25", body.getBoolean("enableBM25", true));
            kbConfig.put("enableReranker", body.getBoolean("enableReranker", false));
            kbConfig.put("chunkSize", body.getInteger("chunkSize", 1000));
            kbConfig.put("chunkOverlap", body.getInteger("chunkOverlap", 200));
            kbConfig.put("scoreThreshold", body.getFloat("scoreThreshold", 0.7f));
            kbConfig.put("topK", body.getInteger("topK", 5));

            kbManager.createKnowledgeBase(kbId, kbConfig);

            ctx.response().setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(kbConfig));
        } catch (Exception e) {
            log.error("Failed to create knowledge base", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void updateKnowledgeBase(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"error\":\"Request body is required\"}");
                return;
            }

            Map<String, Object> updates = new LinkedHashMap<>();
            if (body.containsKey("name")) updates.put("name", body.getString("name"));
            if (body.containsKey("description")) updates.put("description", body.getString("description"));
            if (body.containsKey("searchType")) updates.put("searchType", body.getString("searchType"));
            if (body.containsKey("enableBM25")) updates.put("enableBM25", body.getBoolean("enableBM25"));
            if (body.containsKey("enableReranker")) updates.put("enableReranker", body.getBoolean("enableReranker"));
            if (body.containsKey("chunkSize")) updates.put("chunkSize", body.getInteger("chunkSize"));
            if (body.containsKey("chunkOverlap")) updates.put("chunkOverlap", body.getInteger("chunkOverlap"));
            if (body.containsKey("scoreThreshold")) updates.put("scoreThreshold", body.getFloat("scoreThreshold"));
            if (body.containsKey("topK")) updates.put("topK", body.getInteger("topK"));

            kbManager.updateKnowledgeBase(id, updates);

            Map<String, Object> kb = kbManager.getKnowledgeBaseConfig(id);
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(kb));
        } catch (Exception e) {
            log.error("Failed to update knowledge base", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void deleteKnowledgeBase(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            kbManager.deleteKnowledgeBase(id);
            ctx.response().setStatusCode(204).end();
        } catch (Exception e) {
            log.error("Failed to delete knowledge base", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    // ========== 文档管理 ==========

    private void listDocuments(RoutingContext ctx) {
        try {
            String kbId = ctx.pathParam("id");
            List<String> offsetParam = ctx.queryParams().getAll("offset");
            int offset = offsetParam.isEmpty() ? 0 : Integer.parseInt(offsetParam.get(0));
            List<String> limitParam = ctx.queryParams().getAll("limit");
            int limit = limitParam.isEmpty() ? 20 : Integer.parseInt(limitParam.get(0));

            List<Map<String, Object>> docs = kbManager.listDocuments(kbId, offset, limit);
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(docs));
        } catch (Exception e) {
            log.error("Failed to list documents", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void getDocument(RoutingContext ctx) {
        try {
            String docId = ctx.pathParam("docId");
            Map<String, Object> doc = kbManager.getDocument(docId);
            if (doc == null) {
                ctx.response().setStatusCode(404).end("{\"error\":\"Document not found\"}");
                return;
            }
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(doc));
        } catch (Exception e) {
            log.error("Failed to get document", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void deleteDocument(RoutingContext ctx) {
        try {
            String kbId = ctx.pathParam("id");
            String docId = ctx.pathParam("docId");

            // 1. 删除 MySQL 中的文档和分块（级联删除）
            kbManager.getRepository().deleteDocument(docId);

            // 2. 删除 Milvus 中的向量数据
            KnowledgeBase kb = kbManager.getKnowledgeBase(kbId);
            if (kb != null) {
                kb.deleteDocument(docId);
            }

            ctx.response().setStatusCode(204).end();
        } catch (Exception e) {
            log.error("Failed to delete document", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void uploadDocument(RoutingContext ctx) {
        try {
            String kbId = ctx.pathParam("id");

            List<FileUpload> uploads = ctx.fileUploads();
            if (uploads.isEmpty()) {
                ctx.response().setStatusCode(400).end("{\"error\":\"No file uploaded\"}");
                return;
            }

            FileUpload upload = uploads.get(0);
            String fileName = upload.fileName();
            byte[] fileBytes = Files.readAllBytes(Paths.get(upload.uploadedFileName()));

            // 检查文件大小（50MB 限制）
            if (fileBytes.length > 50 * 1024 * 1024) {
                ctx.response().setStatusCode(400).end("{\"error\":\"File size exceeds 50MB limit\"}");
                return;
            }

            // 异步索引文档
            indexService.uploadAndIndex(kbId, fileName, fileBytes)
                    .thenAccept(docId -> {
                        ObjectNode result = objectMapper.createObjectNode();
                        result.put("id", docId);
                        result.put("status", "pending");
                        result.put("message", "Document uploaded and queued for indexing");
                        ctx.response().setStatusCode(202)
                                .putHeader("Content-Type", "application/json")
                                .end(result.toString());
                    })
                    .exceptionally(e -> {
                        log.error("Failed to upload document", e);
                        ctx.response().setStatusCode(500).end("{\"error\":\"Upload failed\"}");
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to upload document", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    // ========== 分块管理 ==========

    private void listChunks(RoutingContext ctx) {
        try {
            String docId = ctx.pathParam("docId");
            List<Map<String, Object>> chunks = kbManager.listChunks(docId);
            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(chunks));
        } catch (Exception e) {
            log.error("Failed to list chunks", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    private void updateChunk(RoutingContext ctx) {
        try {
            String chunkId = ctx.pathParam("chunkId");
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"error\":\"Request body is required\"}");
                return;
            }
            String content = body.getString("content");
            if (content == null || content.isBlank()) {
                ctx.response().setStatusCode(400).end("{\"error\":\"content is required\"}");
                return;
            }

            // 更新 MySQL 中的分块内容
            kbManager.getRepository().updateChunk(chunkId, content);

            // TODO: 异步更新 Milvus 中的向量（需要重新 embedding）
            // 当前策略：仅更新 MySQL 内容，检索时优先使用 Milvus 返回的 content
            // 后续可通过重新索引来同步 Milvus 中的内容

            ctx.response().setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end("{\"success\":true}");
        } catch (Exception e) {
            log.error("Failed to update chunk", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }

    // ========== 检索 ==========

    private void search(RoutingContext ctx) {
        try {
            String kbId = ctx.pathParam("id");
            JsonObject body = ctx.body().asJsonObject();
            if (body == null) {
                ctx.response().setStatusCode(400).end("{\"error\":\"Request body is required\"}");
                return;
            }
            String query = body.getString("query");
            if (query == null || query.isBlank()) {
                ctx.response().setStatusCode(400).end("{\"error\":\"query is required\"}");
                return;
            }

            KnowledgeBase kb = kbManager.getKnowledgeBase(kbId);
            if (kb == null) {
                ctx.response().setStatusCode(503)
                        .end("{\"error\":\"Vector database not configured, search unavailable\"}");
                return;
            }

            KnowledgeBase.RetrievalConfig retrievalConfig = KnowledgeBase.RetrievalConfig.builder()
                    .topK(body.getInteger("topK", kb.getConfig().getTopK()))
                    .scoreThreshold(body.getFloat("scoreThreshold", kb.getConfig().getScoreThreshold()))
                    .searchType(body.getString("searchType", kb.getConfig().getSearchType()))
                    .build();

            List<KnowledgeBase.DocumentChunk> results = kb.retrieve(query, retrievalConfig)
                    .orTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .join();

            ArrayNode resultArray = objectMapper.createArrayNode();
            for (KnowledgeBase.DocumentChunk chunk : results) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("id", chunk.getId());
                item.put("documentId", chunk.getDocumentId());
                item.put("content", chunk.getContent());
                item.put("chunkIndex", chunk.getChunkIndex());
                item.put("score", chunk.getScore());
                resultArray.add(item);
            }

            ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(objectMapper.writeValueAsString(resultArray));
        } catch (Exception e) {
            log.error("Failed to search knowledge base", e);
            ctx.response().setStatusCode(500).end("{\"error\":\"Internal server error\"}");
        }
    }
}