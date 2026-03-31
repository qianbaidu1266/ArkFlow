package com.langgraph4j.engine.websocket;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionEventBus {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutionEventBus.class);
    
    private final EventBus eventBus;
    private final Map<String, ServerWebSocket> connections = new ConcurrentHashMap<>();
    
    public static final String ADDRESS_PREFIX = "execution.";
    public static final String NODE_STARTED = "node_started";
    public static final String NODE_COMPLETED = "node_completed";
    public static final String NODE_FAILED = "node_failed";
    public static final String EXECUTION_STARTED = "execution_started";
    public static final String EXECUTION_COMPLETED = "execution_completed";
    
    public ExecutionEventBus(Vertx vertx) {
        this.eventBus = vertx.eventBus();
    }
    
    public void addConnection(String executionId, ServerWebSocket ws) {
        connections.put(executionId, ws);
        log.info("WebSocket connected for execution: {}", executionId);
    }
    
    public void removeConnection(String executionId) {
        connections.remove(executionId);
        log.info("WebSocket disconnected for execution: {}", executionId);
    }
    
    public void removeConnection(ServerWebSocket ws) {
        connections.entrySet().removeIf(entry -> entry.getValue().equals(ws));
    }
    
    private void broadcastToExecution(String executionId, JsonObject message) {
        ServerWebSocket ws = connections.get(executionId);
        if (ws != null && !ws.isClosed()) {
            ws.writeTextMessage(message.encode());
        }
    }
    
    public void publishNodeStarted(String executionId, String nodeId, String nodeName, String nodeType) {
        JsonObject data = new JsonObject()
            .put("type", NODE_STARTED)
            .put("executionId", executionId)
            .put("nodeId", nodeId)
            .put("nodeName", nodeName)
            .put("nodeType", nodeType)
            .put("status", "RUNNING")
            .put("timestamp", System.currentTimeMillis());
        
        broadcastToExecution(executionId, data);
        log.debug("Published node started: {} - {}", nodeId, nodeName);
    }
    
    public void publishNodeCompleted(String executionId, String nodeId, String nodeName, 
                                      String nodeType, String status, long duration, 
                                      Map<String, Object> outputs) {
        JsonObject data = new JsonObject()
            .put("type", NODE_COMPLETED)
            .put("executionId", executionId)
            .put("nodeId", nodeId)
            .put("nodeName", nodeName)
            .put("nodeType", nodeType)
            .put("status", status)
            .put("duration", duration)
            .put("timestamp", System.currentTimeMillis());
        
        if (outputs != null && !outputs.isEmpty()) {
            data.put("outputs", outputs);
        }
        
        broadcastToExecution(executionId, data);
        log.debug("Published node completed: {} - {} ({}ms)", nodeId, nodeName, duration);
    }
    
    public void publishNodeFailed(String executionId, String nodeId, String nodeName,
                                   String nodeType, String errorMessage) {
        JsonObject data = new JsonObject()
            .put("type", NODE_FAILED)
            .put("executionId", executionId)
            .put("nodeId", nodeId)
            .put("nodeName", nodeName)
            .put("nodeType", nodeType)
            .put("status", "FAILED")
            .put("errorMessage", errorMessage)
            .put("timestamp", System.currentTimeMillis());
        
        broadcastToExecution(executionId, data);
        log.debug("Published node failed: {} - {}", nodeId, errorMessage);
    }
    
    public void publishExecutionStarted(String executionId, String workflowId) {
        JsonObject data = new JsonObject()
            .put("type", EXECUTION_STARTED)
            .put("executionId", executionId)
            .put("workflowId", workflowId)
            .put("status", "RUNNING")
            .put("timestamp", System.currentTimeMillis());
        
        broadcastToExecution(executionId, data);
        log.info("Published execution started: {}", executionId);
    }
    
    public void publishExecutionCompleted(String executionId, String workflowId, 
                                           boolean success, long duration) {
        JsonObject data = new JsonObject()
            .put("type", EXECUTION_COMPLETED)
            .put("executionId", executionId)
            .put("workflowId", workflowId)
            .put("success", success)
            .put("status", success ? "SUCCESS" : "FAILED")
            .put("duration", duration)
            .put("timestamp", System.currentTimeMillis());
        
        broadcastToExecution(executionId, data);
        log.info("Published execution completed: {} ({}ms)", executionId, duration);
    }
    
    public EventBus getEventBus() {
        return eventBus;
    }
    
    public int getConnectionCount() {
        return connections.size();
    }
}
