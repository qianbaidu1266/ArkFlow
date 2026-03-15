package com.langgraph4j.engine.core;

import com.langgraph4j.engine.model.LLMClient;
import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.rag.KnowledgeBase;
import com.langgraph4j.engine.state.CheckpointManager;
import com.langgraph4j.engine.state.GraphState;
import io.vertx.core.Vertx;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行上下文
 * 包含执行过程中的所有依赖和资源
 */
@Data
@Builder
@Slf4j
public class ExecutionContext {
    
    private final String executionId;
    private final String workflowId;
    private final Vertx vertx;
    private final CheckpointManager checkpointManager;
    private final LLMClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final KnowledgeBase knowledgeBase;
    
    @Builder.Default
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    
    @Builder.Default
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    
    /**
     * 保存检查点
     */
    public void saveCheckpoint(String nodeId, GraphState state) {
        if (checkpointManager != null) {
            checkpointManager.saveCheckpoint(executionId, nodeId, state);
            log.debug("Checkpoint saved for node: {}", nodeId);
        }
    }
    
    /**
     * 恢复检查点
     */
    public GraphState restoreCheckpoint(String nodeId) {
        if (checkpointManager != null) {
            return checkpointManager.restoreCheckpoint(executionId, nodeId);
        }
        return null;
    }
    
    /**
     * 设置变量
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
    
    /**
     * 获取变量
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) variables.get(key);
    }
    
    /**
     * 获取变量（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, T defaultValue) {
        Object value = variables.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 设置元数据
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * 获取元数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }
    
    /**
     * 创建子上下文
     */
    public ExecutionContext createSubContext(String subExecutionId) {
        return ExecutionContext.builder()
            .executionId(subExecutionId)
            .workflowId(workflowId)
            .vertx(vertx)
            .checkpointManager(checkpointManager)
            .llmClient(llmClient)
            .embeddingClient(embeddingClient)
            .knowledgeBase(knowledgeBase)
            .variables(new ConcurrentHashMap<>(variables))
            .metadata(new ConcurrentHashMap<>(metadata))
            .build();
    }
}
