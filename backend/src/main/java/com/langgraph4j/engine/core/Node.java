package com.langgraph4j.engine.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.langgraph4j.engine.state.GraphState;
import com.langgraph4j.engine.state.NodeExecutionSnapshot;
import com.langgraph4j.engine.state.NodeExecutionStatus;
import com.langgraph4j.engine.state.SnapshotManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Data
@Slf4j
public abstract class Node {
    
    protected String id;
    protected String name;
    protected NodeType type;
    protected Map<String, Object> config;
    protected Position position;
    
    public Node(String id, String name, NodeType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }
    
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        SnapshotManager snapshotManager = context.getSnapshotManager();
        NodeExecutionSnapshot snapshot = null;
        
        if (context.getEventBus() != null) {
            context.getEventBus().publishNodeStarted(
                context.getExecutionId(),
                id,
                name,
                type.getCode()
            );
        }
        
        if (snapshotManager != null) {
            snapshot = NodeExecutionSnapshot.create(
                context.getExecutionId(),
                id,
                type.getCode(),
                name
            );
            snapshot.setInputs(extractInputs(state));
            snapshot.markRunning();
            snapshotManager.saveSnapshot(snapshot);
            snapshotManager.updateExecutionStatus(context.getExecutionId(), "RUNNING");
        }
        
        final NodeExecutionSnapshot finalSnapshot = snapshot;
        final long startTime = System.currentTimeMillis();
        
        return doExecute(state, context)
            .thenApply(output -> {
                long duration = System.currentTimeMillis() - startTime;
                
                if (context.getEventBus() != null) {
                    context.getEventBus().publishNodeCompleted(
                        context.getExecutionId(),
                        id,
                        name,
                        type.getCode(),
                        "SUCCESS",
                        duration,
                        extractOutputs(output)
                    );
                }
                
                if (snapshotManager != null && finalSnapshot != null) {
                    Map<String, Object> outputs = extractOutputs(output);
                    Map<String, Object> metadata = buildMetadata(state, output, context);
                    finalSnapshot.markSuccess(outputs, metadata);
                    snapshotManager.updateSnapshot(finalSnapshot);
                }
                return output;
            })
            .exceptionally(e -> {
                long duration = System.currentTimeMillis() - startTime;
                
                if (context.getEventBus() != null) {
                    context.getEventBus().publishNodeFailed(
                        context.getExecutionId(),
                        id,
                        name,
                        type.getCode(),
                        e.getMessage()
                    );
                }
                
                if (snapshotManager != null && finalSnapshot != null) {
                    finalSnapshot.markFailed(e.getMessage(), getStackTrace(e));
                    snapshotManager.updateSnapshot(finalSnapshot);
                    snapshotManager.updateExecutionError(context.getExecutionId(), e.getMessage());
                }
                throw new RuntimeException("Node execution failed: " + id, e);
            });
    }
    
    protected abstract CompletableFuture<GraphState> doExecute(GraphState state, ExecutionContext context);
    
    protected Map<String, Object> extractInputs(GraphState state) {
        return state.getAll();
    }
    
    protected Map<String, Object> extractOutputs(GraphState state) {
        return state.getAll();
    }
    
    protected Map<String, Object> buildMetadata(GraphState input, GraphState output, ExecutionContext context) {
        return Map.of();
    }
    
    public abstract boolean validate();
    
    public abstract Map<String, ParameterDef> getInputParameters();
    
    public abstract Map<String, ParameterDef> getOutputParameters();
    
    public JsonNode getConfigSchema() {
        return null;
    }
    
    public void updateConfig(Map<String, Object> newConfig) {
        if (this.config != null) {
            this.config.putAll(newConfig);
        } else {
            this.config = newConfig;
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, T defaultValue) {
        if (config == null) return defaultValue;
        Object value = config.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    private String getStackTrace(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 5000) break;
        }
        return sb.toString();
    }
    
    @Data
    public static class Position {
        private double x;
        private double y;
        
        public Position() {}
        
        public Position(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    
    @Data
    public static class ParameterDef {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
    }
}
