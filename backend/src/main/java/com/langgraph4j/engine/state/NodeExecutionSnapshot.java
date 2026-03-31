package com.langgraph4j.engine.state;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NodeExecutionSnapshot {
    
    private String id;
    private String executionId;
    private String nodeId;
    private String nodeType;
    private String nodeName;
    
    private NodeExecutionStatus status;
    private Long startTime;
    private Long endTime;
    private Long duration;
    
    private Map<String, Object> inputs;
    private Map<String, Object> outputs;
    
    private String errorMessage;
    private String errorStack;
    
    private Map<String, Object> metadata;
    
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    
    private String inputsStorageKey;
    private String outputsStorageKey;
    private Long inputsSize;
    private Long outputsSize;
    
    private Long createdAt;
    private Long updatedAt;
    
    public static NodeExecutionSnapshot create(String executionId, String nodeId, String nodeType, String nodeName) {
        return NodeExecutionSnapshot.builder()
            .id("snap_" + System.currentTimeMillis() + "_" + nodeId.hashCode())
            .executionId(executionId)
            .nodeId(nodeId)
            .nodeType(nodeType)
            .nodeName(nodeName)
            .status(NodeExecutionStatus.PENDING)
            .createdAt(System.currentTimeMillis())
            .updatedAt(System.currentTimeMillis())
            .build();
    }
    
    public void markRunning() {
        this.status = NodeExecutionStatus.RUNNING;
        this.startTime = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void markSuccess(Map<String, Object> outputs, Map<String, Object> metadata) {
        this.status = NodeExecutionStatus.SUCCESS;
        this.endTime = System.currentTimeMillis();
        this.duration = this.endTime - (this.startTime != null ? this.startTime : this.endTime);
        this.outputs = outputs;
        this.metadata = metadata;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void markFailed(String errorMessage, String errorStack) {
        this.status = NodeExecutionStatus.FAILED;
        this.endTime = System.currentTimeMillis();
        this.duration = this.endTime - (this.startTime != null ? this.startTime : this.endTime);
        this.errorMessage = errorMessage;
        this.errorStack = errorStack;
        this.updatedAt = System.currentTimeMillis();
    }
    
    public void setTokenUsage(int promptTokens, int completionTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = promptTokens + completionTokens;
    }
}
