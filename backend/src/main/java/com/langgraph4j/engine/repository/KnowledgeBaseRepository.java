package com.langgraph4j.engine.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 知识库数据访问层
 * 管理 knowledge_bases、documents、document_chunks 三张 MySQL 表
 */
@Slf4j
public class KnowledgeBaseRepository {

    private final DataSource dataSource;

    public KnowledgeBaseRepository(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);

        this.dataSource = new HikariDataSource(config);

        initializeTables();
    }

    public KnowledgeBaseRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ========== 表初始化 ==========

    private void initializeTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_bases (
                    id VARCHAR(64) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    description TEXT,
                    embedding_model VARCHAR(128),
                    embedding_dimensions INT DEFAULT 1024,
                    search_type VARCHAR(32) DEFAULT 'hybrid',
                    enable_bm25 BOOLEAN DEFAULT TRUE,
                    enable_reranker BOOLEAN DEFAULT FALSE,
                    chunk_size INT DEFAULT 1000,
                    chunk_overlap INT DEFAULT 200,
                    score_threshold FLOAT DEFAULT 0.7,
                    top_k INT DEFAULT 5,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    INDEX idx_name (name),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id VARCHAR(64) PRIMARY KEY,
                    knowledge_base_id VARCHAR(64) NOT NULL,
                    title VARCHAR(512),
                    source_file_name VARCHAR(512),
                    source_file_path VARCHAR(1024),
                    file_type VARCHAR(32),
                    file_size BIGINT DEFAULT 0,
                    word_count INT DEFAULT 0,
                    indexing_status VARCHAR(32) DEFAULT 'pending',
                    chunk_count INT DEFAULT 0,
                    error_message TEXT,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    INDEX idx_kb_id (knowledge_base_id),
                    INDEX idx_status (indexing_status),
                    CONSTRAINT fk_documents_kb FOREIGN KEY (knowledge_base_id)
                        REFERENCES knowledge_bases(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS document_chunks (
                    id VARCHAR(64) PRIMARY KEY,
                    document_id VARCHAR(64) NOT NULL,
                    knowledge_base_id VARCHAR(64) NOT NULL,
                    content TEXT,
                    chunk_index INT DEFAULT 0,
                    token_count INT DEFAULT 0,
                    word_count INT DEFAULT 0,
                    milvus_chunk_id VARCHAR(128),
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    INDEX idx_doc_id (document_id),
                    INDEX idx_kb_id (knowledge_base_id),
                    CONSTRAINT fk_chunks_document FOREIGN KEY (document_id)
                        REFERENCES documents(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

            log.info("Knowledge base MySQL tables initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize knowledge base tables", e);
            throw new RuntimeException("Failed to initialize knowledge base tables", e);
        }
    }

    // ========== 知识库 CRUD ==========

    public void insertKnowledgeBase(Map<String, Object> kb) {
        String sql = """
            INSERT INTO knowledge_bases
            (id, name, description, embedding_model, embedding_dimensions,
             search_type, enable_bm25, enable_reranker,
             chunk_size, chunk_overlap, score_threshold, top_k, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setString(1, (String) kb.get("id"));
            stmt.setString(2, (String) kb.get("name"));
            stmt.setString(3, (String) kb.getOrDefault("description", ""));
            stmt.setString(4, (String) kb.getOrDefault("embeddingModel", ""));
            stmt.setInt(5, (Integer) kb.getOrDefault("embeddingDimensions", 1024));
            stmt.setString(6, (String) kb.getOrDefault("searchType", "hybrid"));
            stmt.setBoolean(7, (Boolean) kb.getOrDefault("enableBM25", true));
            stmt.setBoolean(8, (Boolean) kb.getOrDefault("enableReranker", false));
            stmt.setInt(9, (Integer) kb.getOrDefault("chunkSize", 1000));
            stmt.setInt(10, (Integer) kb.getOrDefault("chunkOverlap", 200));
            stmt.setFloat(11, ((Number) kb.getOrDefault("scoreThreshold", 0.7f)).floatValue());
            stmt.setInt(12, (Integer) kb.getOrDefault("topK", 5));
            stmt.setLong(13, now);
            stmt.setLong(14, now);
            stmt.executeUpdate();
            log.info("Knowledge base created: {}", kb.get("id"));
        } catch (SQLException e) {
            log.error("Failed to insert knowledge base", e);
            throw new RuntimeException("Failed to insert knowledge base", e);
        }
    }

    public Map<String, Object> getKnowledgeBase(String id) {
        String sql = "SELECT * FROM knowledge_bases WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rowToKnowledgeBase(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get knowledge base: {}", id, e);
        }
        return null;
    }

    public List<Map<String, Object>> listKnowledgeBases() {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = """
            SELECT kb.*,
                   (SELECT COUNT(*) FROM documents d WHERE d.knowledge_base_id = kb.id) as doc_count,
                   (SELECT COUNT(*) FROM document_chunks c WHERE c.knowledge_base_id = kb.id) as chunk_count
            FROM knowledge_bases kb
            ORDER BY kb.updated_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> kb = rowToKnowledgeBase(rs);
                kb.put("docCount", rs.getInt("doc_count"));
                kb.put("chunkCount", rs.getInt("chunk_count"));
                result.add(kb);
            }
        } catch (SQLException e) {
            log.error("Failed to list knowledge bases", e);
        }
        return result;
    }

    public void updateKnowledgeBase(String id, Map<String, Object> updates) {
        StringBuilder sql = new StringBuilder("UPDATE knowledge_bases SET updated_at = ?");
        List<Object> params = new ArrayList<>();
        params.add(System.currentTimeMillis());

        if (updates.containsKey("name")) {
            sql.append(", name = ?");
            params.add(updates.get("name"));
        }
        if (updates.containsKey("description")) {
            sql.append(", description = ?");
            params.add(updates.get("description"));
        }
        if (updates.containsKey("searchType")) {
            sql.append(", search_type = ?");
            params.add(updates.get("searchType"));
        }
        if (updates.containsKey("enableBM25")) {
            sql.append(", enable_bm25 = ?");
            params.add(updates.get("enableBM25"));
        }
        if (updates.containsKey("enableReranker")) {
            sql.append(", enable_reranker = ?");
            params.add(updates.get("enableReranker"));
        }
        if (updates.containsKey("chunkSize")) {
            sql.append(", chunk_size = ?");
            params.add(updates.get("chunkSize"));
        }
        if (updates.containsKey("chunkOverlap")) {
            sql.append(", chunk_overlap = ?");
            params.add(updates.get("chunkOverlap"));
        }
        if (updates.containsKey("scoreThreshold")) {
            sql.append(", score_threshold = ?");
            params.add(updates.get("scoreThreshold"));
        }
        if (updates.containsKey("topK")) {
            sql.append(", top_k = ?");
            params.add(updates.get("topK"));
        }
        sql.append(" WHERE id = ?");
        params.add(id);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            stmt.executeUpdate();
            log.info("Knowledge base updated: {}", id);
        } catch (SQLException e) {
            log.error("Failed to update knowledge base: {}", id, e);
            throw new RuntimeException("Failed to update knowledge base", e);
        }
    }

    public void deleteKnowledgeBase(String id) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM knowledge_bases WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
            log.info("Knowledge base deleted: {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete knowledge base: {}", id, e);
            throw new RuntimeException("Failed to delete knowledge base", e);
        }
    }

    // ========== 文档 CRUD ==========

    public void insertDocument(Map<String, Object> doc) {
        String sql = """
            INSERT INTO documents
            (id, knowledge_base_id, title, source_file_name, source_file_path,
             file_type, file_size, word_count, indexing_status, chunk_count,
             error_message, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            stmt.setString(1, (String) doc.get("id"));
            stmt.setString(2, (String) doc.get("knowledgeBaseId"));
            stmt.setString(3, (String) doc.getOrDefault("title", ""));
            stmt.setString(4, (String) doc.getOrDefault("sourceFileName", ""));
            stmt.setString(5, (String) doc.getOrDefault("sourceFilePath", ""));
            stmt.setString(6, (String) doc.getOrDefault("fileType", ""));
            stmt.setLong(7, ((Number) doc.getOrDefault("fileSize", 0L)).longValue());
            stmt.setInt(8, (Integer) doc.getOrDefault("wordCount", 0));
            stmt.setString(9, (String) doc.getOrDefault("indexingStatus", "pending"));
            stmt.setInt(10, (Integer) doc.getOrDefault("chunkCount", 0));
            stmt.setString(11, (String) doc.getOrDefault("errorMessage", null));
            stmt.setLong(12, now);
            stmt.setLong(13, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to insert document", e);
            throw new RuntimeException("Failed to insert document", e);
        }
    }

    public Map<String, Object> getDocument(String id) {
        String sql = "SELECT * FROM documents WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rowToDocument(rs);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get document: {}", id, e);
        }
        return null;
    }

    public List<Map<String, Object>> listDocuments(String knowledgeBaseId, int offset, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = """
            SELECT * FROM documents
            WHERE knowledge_base_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, knowledgeBaseId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToDocument(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list documents for kb: {}", knowledgeBaseId, e);
        }
        return result;
    }

    public void updateDocumentStatus(String id, String status, Integer chunkCount, String errorMessage) {
        String sql = """
            UPDATE documents
            SET indexing_status = ?, chunk_count = ?, error_message = ?, updated_at = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            if (chunkCount != null) {
                stmt.setInt(2, chunkCount);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, errorMessage);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setString(5, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update document status: {}", id, e);
        }
    }

    public void deleteDocument(String id) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM documents WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
            log.info("Document deleted: {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete document: {}", id, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    // ========== 分块 CRUD ==========

    public void insertChunks(List<Map<String, Object>> chunks) {
        String sql = """
            INSERT INTO document_chunks
            (id, document_id, knowledge_base_id, content, chunk_index,
             token_count, word_count, milvus_chunk_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            for (Map<String, Object> chunk : chunks) {
                stmt.setString(1, (String) chunk.get("id"));
                stmt.setString(2, (String) chunk.get("documentId"));
                stmt.setString(3, (String) chunk.get("knowledgeBaseId"));
                stmt.setString(4, (String) chunk.get("content"));
                stmt.setInt(5, (Integer) chunk.getOrDefault("chunkIndex", 0));
                stmt.setInt(6, (Integer) chunk.getOrDefault("tokenCount", 0));
                stmt.setInt(7, (Integer) chunk.getOrDefault("wordCount", 0));
                stmt.setString(8, (String) chunk.get("milvusChunkId"));
                stmt.setLong(9, now);
                stmt.setLong(10, now);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            log.error("Failed to insert chunks", e);
            throw new RuntimeException("Failed to insert chunks", e);
        }
    }

    public List<Map<String, Object>> listChunks(String documentId) {
        List<Map<String, Object>> result = new ArrayList<>();
        String sql = "SELECT * FROM document_chunks WHERE document_id = ? ORDER BY chunk_index ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, documentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToChunk(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list chunks for document: {}", documentId, e);
        }
        return result;
    }

    public void deleteChunksByDocument(String documentId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM document_chunks WHERE document_id = ?")) {
            stmt.setString(1, documentId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete chunks for document: {}", documentId, e);
        }
    }

    public void updateChunk(String chunkId, String content) {
        String sql = "UPDATE document_chunks SET content = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, content);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, chunkId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update chunk: {}", chunkId, e);
        }
    }

    public void deleteChunk(String chunkId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM document_chunks WHERE id = ?")) {
            stmt.setString(1, chunkId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to delete chunk: {}", chunkId, e);
        }
    }

    // ========== 工具方法 ==========

    private Map<String, Object> rowToKnowledgeBase(ResultSet rs) throws SQLException {
        Map<String, Object> kb = new LinkedHashMap<>();
        kb.put("id", rs.getString("id"));
        kb.put("name", rs.getString("name"));
        kb.put("description", rs.getString("description"));
        kb.put("embeddingModel", rs.getString("embedding_model"));
        kb.put("embeddingDimensions", rs.getInt("embedding_dimensions"));
        kb.put("searchType", rs.getString("search_type"));
        kb.put("enableBM25", rs.getBoolean("enable_bm25"));
        kb.put("enableReranker", rs.getBoolean("enable_reranker"));
        kb.put("chunkSize", rs.getInt("chunk_size"));
        kb.put("chunkOverlap", rs.getInt("chunk_overlap"));
        kb.put("scoreThreshold", rs.getFloat("score_threshold"));
        kb.put("topK", rs.getInt("top_k"));
        kb.put("createdAt", rs.getLong("created_at"));
        kb.put("updatedAt", rs.getLong("updated_at"));
        return kb;
    }

    private Map<String, Object> rowToDocument(ResultSet rs) throws SQLException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", rs.getString("id"));
        doc.put("knowledgeBaseId", rs.getString("knowledge_base_id"));
        doc.put("title", rs.getString("title"));
        doc.put("sourceFileName", rs.getString("source_file_name"));
        doc.put("sourceFilePath", rs.getString("source_file_path"));
        doc.put("fileType", rs.getString("file_type"));
        doc.put("fileSize", rs.getLong("file_size"));
        doc.put("wordCount", rs.getInt("word_count"));
        doc.put("indexingStatus", rs.getString("indexing_status"));
        doc.put("chunkCount", rs.getInt("chunk_count"));
        doc.put("errorMessage", rs.getString("error_message"));
        doc.put("createdAt", rs.getLong("created_at"));
        doc.put("updatedAt", rs.getLong("updated_at"));
        return doc;
    }

    private Map<String, Object> rowToChunk(ResultSet rs) throws SQLException {
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", rs.getString("id"));
        chunk.put("documentId", rs.getString("document_id"));
        chunk.put("knowledgeBaseId", rs.getString("knowledge_base_id"));
        chunk.put("content", rs.getString("content"));
        chunk.put("chunkIndex", rs.getInt("chunk_index"));
        chunk.put("tokenCount", rs.getInt("token_count"));
        chunk.put("wordCount", rs.getInt("word_count"));
        chunk.put("milvusChunkId", rs.getString("milvus_chunk_id"));
        chunk.put("createdAt", rs.getLong("created_at"));
        chunk.put("updatedAt", rs.getLong("updated_at"));
        return chunk;
    }

    public void close() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }
}