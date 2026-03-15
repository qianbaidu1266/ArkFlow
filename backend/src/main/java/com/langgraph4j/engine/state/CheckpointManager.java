package com.langgraph4j.engine.state;

import java.util.List;

/**
 * 检查点管理器接口
 * 支持分布式状态持久化
 */
public interface CheckpointManager {
    
    /**
     * 保存检查点
     */
    void saveCheckpoint(String executionId, String nodeId, GraphState state);
    
    /**
     * 恢复检查点
     */
    GraphState restoreCheckpoint(String executionId, String nodeId);
    
    /**
     * 获取执行的所有检查点
     */
    List<Checkpoint> getCheckpoints(String executionId);
    
    /**
     * 获取最新检查点
     */
    Checkpoint getLatestCheckpoint(String executionId);
    
    /**
     * 删除检查点
     */
    void deleteCheckpoint(String executionId, String nodeId);
    
    /**
     * 删除执行的所有检查点
     */
    void deleteExecutionCheckpoints(String executionId);
    
    /**
     * 检查点数据类
     */
    class Checkpoint {
        private String executionId;
        private String nodeId;
        private GraphState state;
        private long timestamp;
        private long sequence;
        
        public Checkpoint() {}
        
        public Checkpoint(String executionId, String nodeId, GraphState state, long timestamp, long sequence) {
            this.executionId = executionId;
            this.nodeId = nodeId;
            this.state = state;
            this.timestamp = timestamp;
            this.sequence = sequence;
        }
        
        // Getters and Setters
        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        
        public GraphState getState() { return state; }
        public void setState(GraphState state) { this.state = state; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public long getSequence() { return sequence; }
        public void setSequence(long sequence) { this.sequence = sequence; }
    }
}
