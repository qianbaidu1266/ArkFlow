package com.langgraph4j.engine.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class JdbcSnapshotManager implements SnapshotManager {
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private static final long LARGE_DATA_THRESHOLD = 1024 * 1024;
    
    public JdbcSnapshotManager(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }
    
    public JdbcSnapshotManager(String jdbcUrl, String username, String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        this.dataSource = ds;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void createExecution(String executionId, String workflowId, Map<String, Object> input) {
        String sql = """
            INSERT INTO workflow_executions 
            (id, workflow_id, status, input, start_time, created_at)
            VALUES (?, ?, 'PENDING', ?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, executionId);
            stmt.setString(2, workflowId);
            stmt.setString(3, toJson(input));
            stmt.setLong(4, System.currentTimeMillis());
            stmt.setLong(5, System.currentTimeMillis());
            
            stmt.executeUpdate();
            log.debug("Created execution record: {}", executionId);
            
        } catch (Exception e) {
            log.error("Failed to create execution record: {}", executionId, e);
            throw new RuntimeException("Failed to create execution record", e);
        }
    }
    
    @Override
    public void saveSnapshot(NodeExecutionSnapshot snapshot) {
        String sql = """
            INSERT INTO node_execution_snapshots 
            (id, execution_id, node_id, node_type, node_name, status, start_time, 
             inputs, metadata, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, snapshot.getId());
            stmt.setString(2, snapshot.getExecutionId());
            stmt.setString(3, snapshot.getNodeId());
            stmt.setString(4, snapshot.getNodeType());
            stmt.setString(5, snapshot.getNodeName());
            stmt.setString(6, snapshot.getStatus().name());
            stmt.setLong(7, snapshot.getStartTime() != null ? snapshot.getStartTime() : 0L);
            stmt.setString(8, toJson(snapshot.getInputs()));
            stmt.setString(9, toJson(snapshot.getMetadata()));
            stmt.setLong(10, snapshot.getCreatedAt());
            stmt.setLong(11, snapshot.getUpdatedAt());
            
            stmt.executeUpdate();
            log.debug("Saved snapshot: {} for node: {}", snapshot.getId(), snapshot.getNodeId());
            
        } catch (Exception e) {
            log.error("Failed to save snapshot for node: {}", snapshot.getNodeId(), e);
            throw new RuntimeException("Failed to save snapshot", e);
        }
    }
    
    @Override
    public void updateSnapshot(NodeExecutionSnapshot snapshot) {
        String sql = """
            UPDATE node_execution_snapshots 
            SET status = ?, start_time = ?, end_time = ?, duration = ?,
                inputs = ?, outputs = ?, error_message = ?, error_stack = ?,
                metadata = ?, prompt_tokens = ?, completion_tokens = ?, total_tokens = ?,
                inputs_storage_key = ?, outputs_storage_key = ?, inputs_size = ?, outputs_size = ?,
                updated_at = ?
            WHERE execution_id = ? AND node_id = ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, snapshot.getStatus().name());
            stmt.setLong(2, snapshot.getStartTime() != null ? snapshot.getStartTime() : 0L);
            stmt.setLong(3, snapshot.getEndTime() != null ? snapshot.getEndTime() : 0L);
            stmt.setLong(4, snapshot.getDuration() != null ? snapshot.getDuration() : 0L);
            stmt.setString(5, toJson(snapshot.getInputs()));
            stmt.setString(6, toJson(snapshot.getOutputs()));
            stmt.setString(7, snapshot.getErrorMessage());
            stmt.setString(8, snapshot.getErrorStack());
            stmt.setString(9, toJson(snapshot.getMetadata()));
            stmt.setInt(10, snapshot.getPromptTokens() != null ? snapshot.getPromptTokens() : 0);
            stmt.setInt(11, snapshot.getCompletionTokens() != null ? snapshot.getCompletionTokens() : 0);
            stmt.setInt(12, snapshot.getTotalTokens() != null ? snapshot.getTotalTokens() : 0);
            stmt.setString(13, snapshot.getInputsStorageKey());
            stmt.setString(14, snapshot.getOutputsStorageKey());
            stmt.setLong(15, snapshot.getInputsSize() != null ? snapshot.getInputsSize() : 0L);
            stmt.setLong(16, snapshot.getOutputsSize() != null ? snapshot.getOutputsSize() : 0L);
            stmt.setLong(17, snapshot.getUpdatedAt());
            stmt.setString(18, snapshot.getExecutionId());
            stmt.setString(19, snapshot.getNodeId());
            
            stmt.executeUpdate();
            log.debug("Updated snapshot for node: {}", snapshot.getNodeId());
            
        } catch (Exception e) {
            log.error("Failed to update snapshot for node: {}", snapshot.getNodeId(), e);
            throw new RuntimeException("Failed to update snapshot", e);
        }
    }
    
    @Override
    public NodeExecutionSnapshot getSnapshot(String executionId, String nodeId) {
        String sql = "SELECT * FROM node_execution_snapshots WHERE execution_id = ? AND node_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, executionId);
            stmt.setString(2, nodeId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSnapshot(rs);
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to get snapshot for execution: {}, node: {}", executionId, nodeId, e);
        }
        
        return null;
    }
    
    @Override
    public List<NodeExecutionSnapshot> getSnapshots(String executionId) {
        String sql = "SELECT * FROM node_execution_snapshots WHERE execution_id = ? ORDER BY start_time ASC";
        List<NodeExecutionSnapshot> snapshots = new ArrayList<>();
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, executionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    snapshots.add(mapResultSetToSnapshot(rs));
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to get snapshots for execution: {}", executionId, e);
        }
        
        return snapshots;
    }
    
    @Override
    public void deleteSnapshots(String executionId) {
        String sql = "DELETE FROM node_execution_snapshots WHERE execution_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, executionId);
            stmt.executeUpdate();
            
        } catch (Exception e) {
            log.error("Failed to delete snapshots for execution: {}", executionId, e);
        }
    }
    
    @Override
    public String storeLargeData(String executionId, String nodeId, String type, Object data) {
        return String.format("executions/%s/%s/%s.json", executionId, nodeId, type);
    }
    
    @Override
    public Object loadLargeData(String storageKey) {
        return null;
    }
    
    @Override
    public void updateExecutionStatus(String executionId, String status) {
        String sql = "UPDATE workflow_executions SET status = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, status);
            stmt.setString(2, executionId);
            stmt.executeUpdate();
            
        } catch (Exception e) {
            log.error("Failed to update execution status: {}", executionId, e);
        }
    }
    
    @Override
    public void updateExecutionResult(String executionId, Map<String, Object> output, int totalTokens) {
        String sql = "UPDATE workflow_executions SET status = ?, output = ?, end_time = ?, duration = end_time - start_time, total_tokens = ? WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, "SUCCESS");
            stmt.setString(2, toJson(output));
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setInt(4, totalTokens);
            stmt.setString(5, executionId);
            stmt.executeUpdate();
            
        } catch (Exception e) {
            log.error("Failed to update execution result: {}", executionId, e);
        }
    }
    
    @Override
    public void updateExecutionError(String executionId, String error) {
        String sql = "UPDATE workflow_executions SET status = ?, error = ?, end_time = ?, duration = end_time - start_time WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, "FAILED");
            stmt.setString(2, error);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setString(4, executionId);
            stmt.executeUpdate();
            
        } catch (Exception e) {
            log.error("Failed to update execution error: {}", executionId, e);
        }
    }
    
    private NodeExecutionSnapshot mapResultSetToSnapshot(ResultSet rs) throws SQLException {
        return NodeExecutionSnapshot.builder()
            .id(rs.getString("id"))
            .executionId(rs.getString("execution_id"))
            .nodeId(rs.getString("node_id"))
            .nodeType(rs.getString("node_type"))
            .nodeName(rs.getString("node_name"))
            .status(NodeExecutionStatus.valueOf(rs.getString("status")))
            .startTime(rs.getLong("start_time"))
            .endTime(rs.getLong("end_time"))
            .duration(rs.getLong("duration"))
            .inputs(parseJson(rs.getString("inputs")))
            .outputs(parseJson(rs.getString("outputs")))
            .errorMessage(rs.getString("error_message"))
            .errorStack(rs.getString("error_stack"))
            .metadata(parseJson(rs.getString("metadata")))
            .promptTokens(rs.getInt("prompt_tokens"))
            .completionTokens(rs.getInt("completion_tokens"))
            .totalTokens(rs.getInt("total_tokens"))
            .inputsStorageKey(rs.getString("inputs_storage_key"))
            .outputsStorageKey(rs.getString("outputs_storage_key"))
            .inputsSize(rs.getLong("inputs_size"))
            .outputsSize(rs.getLong("outputs_size"))
            .createdAt(rs.getLong("created_at"))
            .updatedAt(rs.getLong("updated_at"))
            .build();
    }
    
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize to JSON", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse JSON: {}", json, e);
            return null;
        }
    }
}
