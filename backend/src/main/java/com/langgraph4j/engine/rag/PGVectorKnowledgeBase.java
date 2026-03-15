package com.langgraph4j.engine.rag;

import com.langgraph4j.engine.model.EmbeddingClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PGVector 知识库实现
 * 使用 PostgreSQL + pgvector 扩展进行向量存储和检索
 */
@Slf4j
public class PGVectorKnowledgeBase extends KnowledgeBase {
    
    private final DataSource dataSource;
    private final ExecutorService executorService;
    private final int embeddingDimensions;
    
    public PGVectorKnowledgeBase(String id, String name, String jdbcUrl, 
                                  String username, String password,
                                  EmbeddingClient embeddingClient, 
                                  RetrievalConfig config,
                                  int embeddingDimensions) {
        super(id, name, embeddingClient, config);
        this.embeddingDimensions = embeddingDimensions;
        
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        
        this.dataSource = new HikariDataSource(hikariConfig);
        this.executorService = Executors.newFixedThreadPool(10);
        
        // 初始化表结构
        initializeTables();
    }
    
    private void initializeTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // 启用 pgvector 扩展
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            
            // 创建文档表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS kb_documents (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64) NOT NULL,
                    title TEXT,
                    content TEXT,
                    source TEXT,
                    type VARCHAR(50),
                    created_at BIGINT,
                    updated_at BIGINT,
                    metadata JSONB
                )
                """)
            ;
            
            // 创建文档块表
            stmt.execute(String.format("""
                CREATE TABLE IF NOT EXISTS kb_document_chunks (
                    id VARCHAR(64) PRIMARY KEY,
                    document_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64) NOT NULL,
                    content TEXT,
                    embedding vector(%d),
                    chunk_index INTEGER,
                    metadata JSONB,
                    created_at BIGINT
                )
                """, embeddingDimensions))
            ;
            
            // 创建索引
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_kb_chunks_kb_id 
                ON kb_document_chunks(kb_id)
                """)
            ;
            
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_kb_chunks_document_id 
                ON kb_document_chunks(document_id)
                """)
            ;
            
            // 创建向量索引（使用IVFFlat）
            stmt.execute(String.format("""
                CREATE INDEX IF NOT EXISTS idx_kb_chunks_embedding 
                ON kb_document_chunks 
                USING ivfflat (embedding vector_cosine_ops)
                WITH (lists = 100)
                """, embeddingDimensions))
            ;
            
            log.info("PGVector tables initialized for knowledge base: {}", id);
        } catch (SQLException e) {
            log.error("Failed to initialize PGVector tables", e);
            throw new RuntimeException("Failed to initialize PGVector tables", e);
        }
    }
    
    @Override
    public CompletableFuture<Void> addDocument(Document document) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 分块
                List<String> chunks = splitText(document.getContent(), config.getChunkConfig());
                
                // 获取embedding
                List<float[]> embeddings = embeddingClient.embedDocuments(chunks).join();
                
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    
                    // 插入文档
                    String docSql = """
                        INSERT INTO kb_documents (id, kb_id, title, content, source, type, created_at, updated_at, metadata)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """;
                    
                    try (PreparedStatement docStmt = conn.prepareStatement(docSql)) {
                        docStmt.setString(1, document.getId());
                        docStmt.setString(2, id);
                        docStmt.setString(3, document.getTitle());
                        docStmt.setString(4, document.getContent());
                        docStmt.setString(5, document.getSource());
                        docStmt.setString(6, document.getType());
                        docStmt.setLong(7, document.getCreatedAt());
                        docStmt.setLong(8, document.getUpdatedAt());
                        docStmt.setString(9, toJson(document.getMetadata()));
                        docStmt.executeUpdate();
                    }
                    
                    // 插入文档块
                    String chunkSql = """
                        INSERT INTO kb_document_chunks (id, document_id, kb_id, content, embedding, chunk_index, metadata, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """;
                    
                    try (PreparedStatement chunkStmt = conn.prepareStatement(chunkSql)) {
                        for (int i = 0; i < chunks.size(); i++) {
                            chunkStmt.setString(1, UUID.randomUUID().toString());
                            chunkStmt.setString(2, document.getId());
                            chunkStmt.setString(3, id);
                            chunkStmt.setString(4, chunks.get(i));
                            chunkStmt.setObject(5, toPgVector(embeddings.get(i)));
                            chunkStmt.setInt(6, i);
                            chunkStmt.setString(7, "{}");
                            chunkStmt.setLong(8, System.currentTimeMillis());
                            chunkStmt.addBatch();
                        }
                        chunkStmt.executeBatch();
                    }
                    
                    conn.commit();
                    log.debug("Document added: {}, chunks: {}", document.getId(), chunks.size());
                }
            } catch (Exception e) {
                log.error("Failed to add document", e);
                throw new RuntimeException("Failed to add document", e);
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
            try (Connection conn = dataSource.getConnection()) {
                // 删除文档块
                String chunkSql = "DELETE FROM kb_document_chunks WHERE document_id = ? AND kb_id = ?";
                try (PreparedStatement chunkStmt = conn.prepareStatement(chunkSql)) {
                    chunkStmt.setString(1, documentId);
                    chunkStmt.setString(2, id);
                    chunkStmt.executeUpdate();
                }
                
                // 删除文档
                String docSql = "DELETE FROM kb_documents WHERE id = ? AND kb_id = ?";
                try (PreparedStatement docStmt = conn.prepareStatement(docSql)) {
                    docStmt.setString(1, documentId);
                    docStmt.setString(2, id);
                    docStmt.executeUpdate();
                }
                
                log.debug("Document deleted: {}", documentId);
            } catch (SQLException e) {
                log.error("Failed to delete document", e);
                throw new RuntimeException("Failed to delete document", e);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<DocumentChunk>> retrieve(String query, RetrievalConfig retrievalConfig) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取查询的embedding
                float[] queryEmbedding = embeddingClient.embedQuery(query).join();
                return similaritySearch(queryEmbedding, retrievalConfig.getTopK()).join();
            } catch (Exception e) {
                log.error("Failed to retrieve documents", e);
                return new ArrayList<>();
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<DocumentChunk>> similaritySearch(float[] embedding, int topK) {
        return CompletableFuture.supplyAsync(() -> {
            List<DocumentChunk> results = new ArrayList<>();
            
            String sql = """
                SELECT id, document_id, content, chunk_index, metadata,
                       1 - (embedding <=> ?::vector) as score
                FROM kb_document_chunks
                WHERE kb_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setObject(1, toPgVector(embedding));
                stmt.setString(2, id);
                stmt.setObject(3, toPgVector(embedding));
                stmt.setInt(4, topK);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        DocumentChunk chunk = DocumentChunk.builder()
                            .id(rs.getString("id"))
                            .documentId(rs.getString("document_id"))
                            .content(rs.getString("content"))
                            .chunkIndex(rs.getInt("chunk_index"))
                            .score(rs.getFloat("score"))
                            .metadata(fromJson(rs.getString("metadata")))
                            .build();
                        results.add(chunk);
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to search documents", e);
            }
            
            return results;
        }, executorService);
    }
    
    @Override
    public CompletableFuture<List<DocumentChunk>> hybridSearch(String query, float[] embedding, int topK) {
        // 简化实现：先进行向量搜索，再进行关键词匹配
        return similaritySearch(embedding != null ? embedding : 
            embeddingClient.embedQuery(query).join(), topK * 2)
            .thenApply(chunks -> {
                // 关键词匹配过滤
                String lowerQuery = query.toLowerCase();
                return chunks.stream()
                    .filter(chunk -> chunk.getContent().toLowerCase().contains(lowerQuery))
                    .limit(topK)
                    .toList();
            });
    }
    
    @Override
    public CompletableFuture<List<Document>> listDocuments(int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Document> documents = new ArrayList<>();
            
            String sql = """
                SELECT id, title, content, source, type, created_at, updated_at, metadata
                FROM kb_documents
                WHERE kb_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, id);
                stmt.setInt(2, limit);
                stmt.setInt(3, offset);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Document doc = Document.builder()
                            .id(rs.getString("id"))
                            .title(rs.getString("title"))
                            .content(rs.getString("content"))
                            .source(rs.getString("source"))
                            .type(rs.getString("type"))
                            .createdAt(rs.getLong("created_at"))
                            .updatedAt(rs.getLong("updated_at"))
                            .metadata(fromJson(rs.getString("metadata")))
                            .build();
                        documents.add(doc);
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to list documents", e);
            }
            
            return documents;
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Long> getDocumentCount() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM kb_documents WHERE kb_id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            } catch (SQLException e) {
                log.error("Failed to get document count", e);
            }
            
            return 0L;
        }, executorService);
    }
    
    @Override
    public CompletableFuture<Void> clear() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // 删除文档块
                String chunkSql = "DELETE FROM kb_document_chunks WHERE kb_id = ?";
                try (PreparedStatement chunkStmt = conn.prepareStatement(chunkSql)) {
                    chunkStmt.setString(1, id);
                    chunkStmt.executeUpdate();
                }
                
                // 删除文档
                String docSql = "DELETE FROM kb_documents WHERE kb_id = ?";
                try (PreparedStatement docStmt = conn.prepareStatement(docSql)) {
                    docStmt.setString(1, id);
                    docStmt.executeUpdate();
                }
                
                log.info("Knowledge base cleared: {}", id);
            } catch (SQLException e) {
                log.error("Failed to clear knowledge base", e);
                throw new RuntimeException("Failed to clear knowledge base", e);
            }
        }, executorService);
    }
    
    private String toPgVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String toJson(java.util.Map<String, Object> map) {
        if (map == null) return "{}";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> fromJson(String json) {
        if (json == null) return new java.util.HashMap<>();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, java.util.Map.class);
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }
}
