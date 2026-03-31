package com.langgraph4j.engine.state;

import java.util.List;
import java.util.Map;

public interface SnapshotManager {
    
    void createExecution(String executionId, String workflowId, Map<String, Object> input);
    
    void saveSnapshot(NodeExecutionSnapshot snapshot);
    
    void updateSnapshot(NodeExecutionSnapshot snapshot);
    
    NodeExecutionSnapshot getSnapshot(String executionId, String nodeId);
    
    List<NodeExecutionSnapshot> getSnapshots(String executionId);
    
    void deleteSnapshots(String executionId);
    
    String storeLargeData(String executionId, String nodeId, String type, Object data);
    
    Object loadLargeData(String storageKey);
    
    void updateExecutionStatus(String executionId, String status);
    
    void updateExecutionResult(String executionId, Map<String, Object> output, int totalTokens);
    
    void updateExecutionError(String executionId, String error);
}
