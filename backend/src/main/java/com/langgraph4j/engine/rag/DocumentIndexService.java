package com.langgraph4j.engine.rag;

import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.repository.KnowledgeBaseRepository;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 文档索引服务
 * 负责文档上传后的异步索引 Pipeline：
 * 1. 保存文件到磁盘
 * 2. 创建 MySQL 文档记录
 * 3. 异步：提取文本 → 分块 → Embedding → 写入 Milvus → 更新 MySQL 状态
 */
@Slf4j
public class DocumentIndexService {

    private final KnowledgeBaseManager kbManager;
    private final KnowledgeBaseRepository repository;
    private final DocumentExtractor extractor;
    private final String uploadDir;
    private final ExecutorService indexExecutor;

    public DocumentIndexService(KnowledgeBaseManager kbManager, String uploadDir) {
        this.kbManager = kbManager;
        this.repository = kbManager.getRepository();
        this.extractor = new DocumentExtractor();
        this.uploadDir = uploadDir;
        this.indexExecutor = kbManager.getIndexExecutor();

        // 确保上传目录存在
        try {
            Files.createDirectories(Path.of(uploadDir));
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", uploadDir, e);
        }
    }

    /**
     * 上传并索引文档
     *
     * @param kbId       知识库 ID
     * @param fileName   原始文件名
     * @param fileBytes  文件字节内容
     * @return 文档 ID
     */
    public CompletableFuture<String> uploadAndIndex(String kbId, String fileName, byte[] fileBytes) {
        String docId = UUID.randomUUID().toString();
        String fileType = DocumentExtractor.getFileExtension(fileName);
        String savedPath = saveFile(docId, fileName, fileBytes);

        // 1. 创建 MySQL 文档记录（状态：pending）
        Map<String, Object> docRecord = new LinkedHashMap<>();
        docRecord.put("id", docId);
        docRecord.put("knowledgeBaseId", kbId);
        docRecord.put("title", fileName);
        docRecord.put("sourceFileName", fileName);
        docRecord.put("sourceFilePath", savedPath);
        docRecord.put("fileType", fileType);
        docRecord.put("fileSize", (long) fileBytes.length);
        docRecord.put("indexingStatus", "pending");
        repository.insertDocument(docRecord);

        // 2. 异步索引
        CompletableFuture.runAsync(() -> {
            try {
                indexDocument(kbId, docId, savedPath, fileType);
            } catch (Exception e) {
                log.error("Failed to index document: {}", docId, e);
                repository.updateDocumentStatus(docId, "error", null, e.getMessage());
            }
        }, indexExecutor);

        return CompletableFuture.completedFuture(docId);
    }

    /**
     * 重新索引文档（用于覆盖上传）
     */
    public CompletableFuture<String> reindexDocument(String kbId, String docId,
                                                      String fileName, byte[] fileBytes) {
        String fileType = DocumentExtractor.getFileExtension(fileName);
        String savedPath = saveFile(docId, fileName, fileBytes);

        // 更新 MySQL 文档记录
        Map<String, Object> docRecord = new LinkedHashMap<>();
        docRecord.put("id", docId);
        docRecord.put("knowledgeBaseId", kbId);
        docRecord.put("title", fileName);
        docRecord.put("sourceFileName", fileName);
        docRecord.put("sourceFilePath", savedPath);
        docRecord.put("fileType", fileType);
        docRecord.put("fileSize", (long) fileBytes.length);
        docRecord.put("indexingStatus", "pending");
        // 删除旧文档记录和分块
        repository.deleteDocument(docId);
        repository.insertDocument(docRecord);

        // 删除 Milvus 中的旧向量
        KnowledgeBase kb = kbManager.getKnowledgeBase(kbId);
        if (kb != null) {
            kb.deleteDocument(docId);
        }

        // 异步索引
        CompletableFuture.runAsync(() -> {
            try {
                indexDocument(kbId, docId, savedPath, fileType);
            } catch (Exception e) {
                log.error("Failed to reindex document: {}", docId, e);
                repository.updateDocumentStatus(docId, "error", null, e.getMessage());
            }
        }, indexExecutor);

        return CompletableFuture.completedFuture(docId);
    }

    /**
     * 执行文档索引
     */
    private void indexDocument(String kbId, String docId, String filePath, String fileType) {
        try {
            log.info("Indexing document: {} (type: {})", docId, fileType);

            // 更新状态为 indexing
            repository.updateDocumentStatus(docId, "indexing", null, null);

            // 1. 提取文本
            String text = extractor.extract(filePath, fileType);
            if (text == null || text.isBlank()) {
                throw new IOException("Extracted text is empty");
            }

            // 2. 获取知识库配置（从 MySQL，不依赖 Milvus）
            Map<String, Object> kbConfig = kbManager.getKnowledgeBaseConfig(kbId);
            int chunkSize = (Integer) kbConfig.getOrDefault("chunkSize", 1000);
            int chunkOverlap = (Integer) kbConfig.getOrDefault("chunkOverlap", 200);

            // 3. 分块
            List<String> chunks = splitText(text, chunkSize, chunkOverlap);
            if (chunks.isEmpty()) {
                throw new IOException("No chunks generated from document");
            }

            KnowledgeBase kb = kbManager.getKnowledgeBase(kbId);

            // 4. 写入分块记录到 MySQL
            List<Map<String, Object>> chunkRecords = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunkId = UUID.randomUUID().toString();
                Map<String, Object> chunkRecord = new LinkedHashMap<>();
                chunkRecord.put("id", chunkId);
                chunkRecord.put("documentId", docId);
                chunkRecord.put("knowledgeBaseId", kbId);
                chunkRecord.put("content", chunks.get(i));
                chunkRecord.put("chunkIndex", i);
                chunkRecord.put("tokenCount", estimateTokenCount(chunks.get(i)));
                chunkRecord.put("wordCount", chunks.get(i).split("\\s+").length);
                chunkRecord.put("milvusChunkId", chunkId);
                chunkRecords.add(chunkRecord);
            }
            repository.insertChunks(chunkRecords);

            // 5. Embedding + 写入 Milvus（仅在有 Milvus 时执行）
            if (kb != null && kbManager.getMilvusClient() != null) {
                EmbeddingClient embeddingClient = kb.getEmbeddingClient();
                List<float[]> embeddings = embeddingClient.embedDocuments(chunks).join();

                List<JsonObject> rows = new ArrayList<>();
                com.google.gson.Gson gson = new com.google.gson.Gson();

                for (int i = 0; i < chunks.size(); i++) {
                    String chunkId = chunkRecords.get(i).get("id").toString();
                    JsonObject row = new JsonObject();
                    row.addProperty("chunk_id", chunkId);
                    row.addProperty("knowledge_base_id", kbId);
                    row.addProperty("document_id", docId);
                    row.addProperty("content", chunks.get(i));
                    row.addProperty("chunk_index", i);
                    row.add("embedding", toJsonArray(embeddings.get(i)));

                    if (kbConfig.getOrDefault("enableBM25", true).equals(true)) {
                        SortedMap<Long, Float> sparseMap = Bm25Util.tokenizeToSparseMap(chunks.get(i));
                        row.add("sparse_embedding", gson.toJsonTree(sparseMap));
                    }
                    rows.add(row);
                }

                io.milvus.v2.service.vector.request.InsertReq insertReq =
                        io.milvus.v2.service.vector.request.InsertReq.builder()
                                .collectionName(kbManager.getCollectionName())
                                .data(rows)
                                .build();
                kbManager.getMilvusClient().insert(insertReq);
            } else {
                log.info("Milvus not available, document {} indexed to MySQL only ({} chunks)", docId, chunks.size());
            }

            // 6. 更新文档状态为 completed
            repository.updateDocumentStatus(docId, "completed", chunks.size(), null);

            log.info("Document indexed successfully: {} ({} chunks)", docId, chunks.size());
        } catch (Exception e) {
            log.error("Failed to index document: {}", docId, e);
            repository.updateDocumentStatus(docId, "error", null, e.getMessage());
        }
    }

    /**
     * 简单文本分块（不依赖 KnowledgeBase 实例）
     */
    private List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        String separator = "\n\n";
        String[] paragraphs = text.split(separator);

        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                // 保留重叠部分
                String currentStr = current.toString();
                int overlapStart = Math.max(0, currentStr.length() - chunkOverlap);
                current = new StringBuilder(currentStr.substring(overlapStart));
            }
            if (!current.isEmpty()) {
                current.append(separator);
            }
            current.append(paragraph);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    /**
     * 保存文件到磁盘
     */
    private String saveFile(String docId, String fileName, byte[] fileBytes) {
        try {
            String ext = DocumentExtractor.getFileExtension(fileName);
            String savedName = docId + (ext.isEmpty() ? "" : "." + ext);
            Path targetPath = Paths.get(uploadDir, savedName);
            Files.write(targetPath, fileBytes);
            log.debug("File saved: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("Failed to save file: {}", fileName, e);
            throw new RuntimeException("Failed to save file", e);
        }
    }

    /**
     * 估算 token 数量（粗略：1 token ≈ 0.75 个英文单词，中文约 1.5 字/token）
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 粗略估算：中文字符约 1.5 字/token，英文约 4 字符/token
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) (chineseChars / 1.5 + otherChars / 4.0);
    }

    private com.google.gson.JsonArray toJsonArray(float[] arr) {
        com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
        for (float v : arr) {
            jsonArray.add(v);
        }
        return jsonArray;
    }
}