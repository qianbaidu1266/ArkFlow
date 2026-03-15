package com.langgraph4j.engine.rag;

import com.langgraph4j.engine.model.EmbeddingClient;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 知识库 - RAG核心组件
 * 支持文档存储、检索和向量搜索
 */
@Slf4j
public abstract class KnowledgeBase {
    
    protected final String id;
    protected final String name;
    protected final EmbeddingClient embeddingClient;
    protected final RetrievalConfig config;
    
    public KnowledgeBase(String id, String name, EmbeddingClient embeddingClient, RetrievalConfig config) {
        this.id = id;
        this.name = name;
        this.embeddingClient = embeddingClient;
        this.config = config != null ? config : RetrievalConfig.builder().build();
    }
    
    /**
     * 添加文档
     */
    public abstract CompletableFuture<Void> addDocument(Document document);
    
    /**
     * 批量添加文档
     */
    public abstract CompletableFuture<Void> addDocuments(List<Document> documents);
    
    /**
     * 删除文档
     */
    public abstract CompletableFuture<Void> deleteDocument(String documentId);
    
    /**
     * 检索相关文档
     */
    public CompletableFuture<List<DocumentChunk>> retrieve(String query) {
        return retrieve(query, config);
    }
    
    /**
     * 检索相关文档（带配置）
     */
    public abstract CompletableFuture<List<DocumentChunk>> retrieve(String query, RetrievalConfig retrievalConfig);
    
    /**
     * 相似性搜索
     */
    public abstract CompletableFuture<List<DocumentChunk>> similaritySearch(float[] embedding, int topK);
    
    /**
     * 混合搜索（关键词 + 向量）
     */
    public CompletableFuture<List<DocumentChunk>> hybridSearch(String query, int topK) {
        return hybridSearch(query, null, topK);
    }
    
    /**
     * 混合搜索（带embedding）
     */
    public abstract CompletableFuture<List<DocumentChunk>> hybridSearch(String query, float[] embedding, int topK);
    
    /**
     * 获取文档列表
     */
    public abstract CompletableFuture<List<Document>> listDocuments(int offset, int limit);
    
    /**
     * 获取文档数量
     */
    public abstract CompletableFuture<Long> getDocumentCount();
    
    /**
     * 清空知识库
     */
    public abstract CompletableFuture<Void> clear();
    
    /**
     * 文档分块
     */
    protected List<String> splitText(String text, ChunkConfig chunkConfig) {
        List<String> chunks = new ArrayList<>();
        
        int chunkSize = chunkConfig.getChunkSize();
        int chunkOverlap = chunkConfig.getChunkOverlap();
        String separator = chunkConfig.getSeparator();
        
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        
        // 按分隔符分割
        String[] splits = text.split(separator);
        
        StringBuilder currentChunk = new StringBuilder();
        for (String split : splits) {
            if (currentChunk.length() + split.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    // 保留重叠部分
                    if (chunkOverlap > 0 && currentChunk.length() > chunkOverlap) {
                        currentChunk = new StringBuilder(
                            currentChunk.substring(currentChunk.length() - chunkOverlap)
                        );
                    } else {
                        currentChunk = new StringBuilder();
                    }
                }
            }
            if (currentChunk.length() > 0) {
                currentChunk.append(separator);
            }
            currentChunk.append(split);
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public EmbeddingClient getEmbeddingClient() { return embeddingClient; }
    public RetrievalConfig getConfig() { return config; }
    
    // 文档类
    @Data
    @Builder
    public static class Document {
        private String id;
        private String title;
        private String content;
        private String source;
        private String type;
        private long createdAt;
        private long updatedAt;
        private java.util.Map<String, Object> metadata;
    }
    
    // 文档块类
    @Data
    @Builder
    public static class DocumentChunk {
        private String id;
        private String documentId;
        private String content;
        private float[] embedding;
        private int chunkIndex;
        private float score;
        private java.util.Map<String, Object> metadata;
    }
    
    // 检索配置
    @Data
    @Builder
    public static class RetrievalConfig {
        @Builder.Default
        private int topK = 5;
        @Builder.Default
        private float scoreThreshold = 0.7f;
        @Builder.Default
        private String searchType = "similarity";  // similarity, mmr, hybrid
        @Builder.Default
        private float diversityLambda = 0.5f;  // MMR多样性参数
        @Builder.Default
        private ChunkConfig chunkConfig = ChunkConfig.builder().build();
    }
    
    // 分块配置
    @Data
    @Builder
    public static class ChunkConfig {
        @Builder.Default
        private int chunkSize = 1000;
        @Builder.Default
        private int chunkOverlap = 200;
        @Builder.Default
        private String separator = "\n\n";
    }
}
