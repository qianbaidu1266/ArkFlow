package com.langgraph4j.engine.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langgraph4j.engine.core.Edge;
import com.langgraph4j.engine.core.Graph;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.node.NodeFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Slf4j
public class WorkflowRepository {
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    public WorkflowRepository(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        
        this.dataSource = new HikariDataSource(config);
        this.objectMapper = new ObjectMapper();
        
        initializeTables();
    }
    
    public WorkflowRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }
    
    private void initializeTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workflows (
                    id VARCHAR(64) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    description TEXT,
                    entry_point VARCHAR(64),
                    status VARCHAR(20) DEFAULT 'draft',
                    version INT DEFAULT 1,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    created_by VARCHAR(64),
                    metadata JSON,
                    INDEX idx_status (status),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workflow_nodes (
                    id VARCHAR(64) PRIMARY KEY,
                    workflow_id VARCHAR(64) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    type VARCHAR(50) NOT NULL,
                    config JSON,
                    position_x DOUBLE DEFAULT 0,
                    position_y DOUBLE DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    INDEX idx_workflow_id (workflow_id),
                    CONSTRAINT fk_workflow_nodes_workflow FOREIGN KEY (workflow_id) 
                        REFERENCES workflows(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workflow_edges (
                    id VARCHAR(64) PRIMARY KEY,
                    workflow_id VARCHAR(64) NOT NULL,
                    from_node VARCHAR(64) NOT NULL,
                    to_node VARCHAR(64),
                    type VARCHAR(20) DEFAULT 'normal',
                    conditions JSON,
                    default_target VARCHAR(64),
                    priority INT DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    INDEX idx_workflow_id (workflow_id),
                    CONSTRAINT fk_workflow_edges_workflow FOREIGN KEY (workflow_id) 
                        REFERENCES workflows(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS workflow_executions (
                    id VARCHAR(64) PRIMARY KEY,
                    workflow_id VARCHAR(64) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    input JSON,
                    output JSON,
                    error TEXT,
                    start_time BIGINT NOT NULL,
                    end_time BIGINT,
                    duration BIGINT,
                    created_at BIGINT NOT NULL,
                    INDEX idx_workflow_id (workflow_id),
                    INDEX idx_status (status),
                    CONSTRAINT fk_executions_workflow FOREIGN KEY (workflow_id) 
                        REFERENCES workflows(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
            
            log.info("MySQL tables initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize database tables", e);
            throw new RuntimeException("Failed to initialize database tables", e);
        }
    }
    
    public void saveWorkflow(Graph workflow) {
        long now = System.currentTimeMillis();
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            String upsertWorkflow = """
                INSERT INTO workflows (id, name, entry_point, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 'draft', 1, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    entry_point = VALUES(entry_point),
                    updated_at = VALUES(updated_at)
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(upsertWorkflow)) {
                stmt.setString(1, workflow.getId());
                stmt.setString(2, workflow.getName());
                stmt.setString(3, workflow.getEntryPoint());
                stmt.setLong(4, now);
                stmt.setLong(5, now);
                stmt.executeUpdate();
            }
            
            String deleteNodes = "DELETE FROM workflow_nodes WHERE workflow_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteNodes)) {
                stmt.setString(1, workflow.getId());
                stmt.executeUpdate();
            }
            
            String insertNode = """
                INSERT INTO workflow_nodes (id, workflow_id, name, type, config, position_x, position_y, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = conn.prepareStatement(insertNode)) {
                for (Node node : workflow.getNodes().values()) {
                    stmt.setString(1, node.getId());
                    stmt.setString(2, workflow.getId());
                    stmt.setString(3, node.getName());
                    stmt.setString(4, node.getType().getCode());
                    stmt.setString(5, objectMapper.writeValueAsString(node.getConfig()));
                    stmt.setDouble(6, node.getPosition() != null ? node.getPosition().getX() : 0);
                    stmt.setDouble(7, node.getPosition() != null ? node.getPosition().getY() : 0);
                    stmt.setLong(8, now);
                    stmt.setLong(9, now);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            
            String deleteEdges = "DELETE FROM workflow_edges WHERE workflow_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteEdges)) {
                stmt.setString(1, workflow.getId());
                stmt.executeUpdate();
            }
            
            String insertEdge = """
                INSERT INTO workflow_edges (id, workflow_id, from_node, to_node, type, conditions, default_target, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement stmt = conn.prepareStatement(insertEdge)) {
                for (Map.Entry<String, List<Edge>> entry : workflow.getEdges().entrySet()) {
                    for (Edge edge : entry.getValue()) {
                        stmt.setString(1, UUID.randomUUID().toString());
                        stmt.setString(2, workflow.getId());
                        stmt.setString(3, edge.getFrom());
                        stmt.setString(4, edge.getTo());
                        stmt.setString(5, edge.getType().getCode());
                        
                        if (edge.getCondition() != null) {
                            stmt.setString(6, objectMapper.writeValueAsString(edge.getCondition().getConditions()));
                            stmt.setString(7, edge.getCondition().getDefaultTarget());
                        } else {
                            stmt.setNull(6, Types.VARCHAR);
                            stmt.setNull(7, Types.VARCHAR);
                        }
                        stmt.setLong(8, now);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
            
            conn.commit();
            log.info("Workflow saved: {}", workflow.getId());
        } catch (Exception e) {
            log.error("Failed to save workflow", e);
            throw new RuntimeException("Failed to save workflow", e);
        }
    }
    
    public Graph loadWorkflow(String id) {
        try (Connection conn = dataSource.getConnection()) {
            String workflowSql = "SELECT id, name, entry_point FROM workflows WHERE id = ?";
            Graph graph = null;
            
            try (PreparedStatement stmt = conn.prepareStatement(workflowSql)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        graph = new Graph(rs.getString("id"), rs.getString("name"));
                        graph.setEntryPoint(rs.getString("entry_point"));
                    }
                }
            }
            
            if (graph == null) {
                return null;
            }
            
            String nodesSql = "SELECT id, name, type, config, position_x, position_y FROM workflow_nodes WHERE workflow_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(nodesSql)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String nodeId = rs.getString("id");
                        String name = rs.getString("name");
                        String typeCode = rs.getString("type");
                        String configJson = rs.getString("config");
                        double posX = rs.getDouble("position_x");
                        double posY = rs.getDouble("position_y");
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> config = configJson != null ? 
                            objectMapper.readValue(configJson, Map.class) : new HashMap<>();
                        
                        Node node = NodeFactory.createNode(nodeId, name, typeCode, config);
                        node.setPosition(new Node.Position(posX, posY));
                        graph.addNode(node);
                    }
                }
            }
            
            String edgesSql = "SELECT from_node, to_node, type, conditions, default_target FROM workflow_edges WHERE workflow_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(edgesSql)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String from = rs.getString("from_node");
                        String to = rs.getString("to_node");
                        String type = rs.getString("type");
                        String conditionsJson = rs.getString("conditions");
                        String defaultTarget = rs.getString("default_target");
                        
                        if ("conditional".equals(type) && conditionsJson != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, String> conditions = objectMapper.readValue(conditionsJson, Map.class);
                            graph.addConditionalEdge(from, conditions, defaultTarget);
                        } else if (to != null) {
                            graph.addEdge(from, to);
                        }
                    }
                }
            }
            
            return graph;
        } catch (Exception e) {
            log.error("Failed to load workflow: {}", id, e);
            throw new RuntimeException("Failed to load workflow", e);
        }
    }
    
    public List<Map<String, Object>> listWorkflows(int offset, int limit) {
        List<Map<String, Object>> workflows = new ArrayList<>();
        
        String sql = """
            SELECT w.id, w.name, w.entry_point, w.status, w.version, w.created_at, w.updated_at,
                   (SELECT COUNT(*) FROM workflow_nodes n WHERE n.workflow_id = w.id) as node_count,
                   (SELECT COUNT(*) FROM workflow_edges e WHERE e.workflow_id = w.id) as edge_count
            FROM workflows w
            ORDER BY w.updated_at DESC
            LIMIT ? OFFSET ?
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> workflow = new HashMap<>();
                    workflow.put("id", rs.getString("id"));
                    workflow.put("name", rs.getString("name"));
                    workflow.put("entryPoint", rs.getString("entry_point"));
                    workflow.put("status", rs.getString("status"));
                    workflow.put("version", rs.getInt("version"));
                    workflow.put("createdAt", rs.getLong("created_at"));
                    workflow.put("updatedAt", rs.getLong("updated_at"));
                    workflow.put("nodeCount", rs.getInt("node_count"));
                    workflow.put("edgeCount", rs.getInt("edge_count"));
                    workflows.add(workflow);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to list workflows", e);
        }
        
        return workflows;
    }
    
    public void deleteWorkflow(String id) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "DELETE FROM workflows WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
            }
            log.info("Workflow deleted: {}", id);
        } catch (SQLException e) {
            log.error("Failed to delete workflow: {}", id, e);
            throw new RuntimeException("Failed to delete workflow", e);
        }
    }
    
    public boolean existsWorkflow(String id) {
        String sql = "SELECT 1 FROM workflows WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check workflow existence: {}", id, e);
            return false;
        }
    }
    
    public void saveExecution(String executionId, String workflowId, String status,
                              Map<String, Object> input, Map<String, Object> output,
                              String error, long startTime, long endTime, long duration) {
        String sql = """
            INSERT INTO workflow_executions (id, workflow_id, status, input, output, error, start_time, end_time, duration, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, executionId);
            stmt.setString(2, workflowId);
            stmt.setString(3, status);
            stmt.setString(4, input != null ? objectMapper.writeValueAsString(input) : null);
            stmt.setString(5, output != null ? objectMapper.writeValueAsString(output) : null);
            stmt.setString(6, error);
            stmt.setLong(7, startTime);
            stmt.setLong(8, endTime);
            stmt.setLong(9, duration);
            stmt.setLong(10, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to save execution", e);
        }
    }
    
    public Map<String, Object> getExecution(String executionId) {
        String sql = "SELECT * FROM workflow_executions WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, executionId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> execution = new HashMap<>();
                    execution.put("id", rs.getString("id"));
                    execution.put("workflowId", rs.getString("workflow_id"));
                    execution.put("status", rs.getString("status"));
                    execution.put("input", rs.getString("input"));
                    execution.put("output", rs.getString("output"));
                    execution.put("error", rs.getString("error"));
                    execution.put("startTime", rs.getLong("start_time"));
                    execution.put("endTime", rs.getLong("end_time"));
                    execution.put("duration", rs.getLong("duration"));
                    return execution;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get execution: {}", executionId, e);
        }
        
        return null;
    }
    
    public void close() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }
}
