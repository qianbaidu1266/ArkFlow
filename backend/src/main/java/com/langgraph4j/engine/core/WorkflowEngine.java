package com.langgraph4j.engine.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langgraph4j.engine.model.LLMClient;
import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.rag.KnowledgeBase;
import com.langgraph4j.engine.repository.WorkflowRepository;
import com.langgraph4j.engine.state.CheckpointManager;
import com.langgraph4j.engine.state.GraphState;
import io.vertx.core.Vertx;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流引擎
 * 管理和执行工作流
 */
@Slf4j
public class WorkflowEngine {
    
    private final Vertx vertx;
    private final ObjectMapper objectMapper;
    private final Map<String, Graph> workflows;
    private final Map<String, ExecutionResult> executionResults;
    
    // 依赖组件
    private CheckpointManager checkpointManager;
    private LLMClient llmClient;
    private EmbeddingClient embeddingClient;
    private KnowledgeBase knowledgeBase;
    private WorkflowRepository repository;
    
    public WorkflowEngine() {
        this.vertx = Vertx.vertx();
        this.objectMapper = new ObjectMapper();
        this.workflows = new ConcurrentHashMap<>();
        this.executionResults = new ConcurrentHashMap<>();
    }
    
    /**
     * 注册工作流（同时持久化到数据库）
     */
    public void registerWorkflow(String id, Graph workflow) {
        if (!workflow.validate()) {
            throw new IllegalArgumentException("Invalid workflow: " + id);
        }
        workflows.put(id, workflow);
        
        if (repository != null) {
            repository.saveWorkflow(workflow);
        }
        
        log.info("Workflow registered: {}", id);
    }
    
    /**
     * 保存工作流到数据库
     */
    public void saveWorkflow(Graph workflow) {
        if (repository != null) {
            repository.saveWorkflow(workflow);
            workflows.put(workflow.getId(), workflow);
            log.info("Workflow saved to database: {}", workflow.getId());
        }
    }
    
    /**
     * 获取工作流（优先从内存，其次从数据库）
     */
    public Graph getWorkflow(String id) {
        Graph workflow = workflows.get(id);
        if (workflow == null && repository != null) {
            workflow = repository.loadWorkflow(id);
            if (workflow != null) {
                workflows.put(id, workflow);
            }
        }
        return workflow;
    }
    
    /**
     * 删除工作流
     */
    public void removeWorkflow(String id) {
        workflows.remove(id);
        
        if (repository != null) {
            repository.deleteWorkflow(id);
        }
        
        log.info("Workflow removed: {}", id);
    }
    
    /**
     * 列出所有工作流
     */
    public List<Map<String, Object>> listWorkflows(int offset, int limit) {
        if (repository != null) {
            return repository.listWorkflows(offset, limit);
        }
        return List.of();
    }
    
    /**
     * 检查工作流是否存在
     */
    public boolean existsWorkflow(String id) {
        if (workflows.containsKey(id)) {
            return true;
        }
        if (repository != null) {
            return repository.existsWorkflow(id);
        }
        return false;
    }
    
    /**
     * 执行工作流
     */
    public CompletableFuture<ExecutionResult> execute(String workflowId, GraphState input) {
        return execute(workflowId, input, null);
    }
    
    /**
     * 执行工作流（带配置）
     */
    public CompletableFuture<ExecutionResult> execute(String workflowId, GraphState input, ExecutionConfig config) {
        Graph workflow = workflows.get(workflowId);
        if (workflow == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Workflow not found: " + workflowId)
            );
        }
        
        String executionId = generateExecutionId();
        log.info("Starting workflow execution: {} (workflow: {})", executionId, workflowId);
        
        // 创建执行上下文
        ExecutionContext context = buildExecutionContext(executionId, workflowId, config);
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        // 执行工作流
        return workflow.execute(input, context)
            .thenApply(output -> {
                long endTime = System.currentTimeMillis();
                
                ExecutionResult result = ExecutionResult.builder()
                    .executionId(executionId)
                    .workflowId(workflowId)
                    .success(true)
                    .output(output)
                    .startTime(startTime)
                    .endTime(endTime)
                    .duration(endTime - startTime)
                    .build();
                
                executionResults.put(executionId, result);
                log.info("Workflow execution completed: {} ({}ms)", executionId, result.getDuration());
                
                return result;
            })
            .exceptionally(e -> {
                long endTime = System.currentTimeMillis();
                
                ExecutionResult result = ExecutionResult.builder()
                    .executionId(executionId)
                    .workflowId(workflowId)
                    .success(false)
                    .error(e.getMessage())
                    .startTime(startTime)
                    .endTime(endTime)
                    .duration(endTime - startTime)
                    .build();
                
                executionResults.put(executionId, result);
                log.error("Workflow execution failed: {}", executionId, e);
                
                return result;
            });
    }
    
    /**
     * 从检查点恢复执行
     */
    public CompletableFuture<ExecutionResult> resumeFromCheckpoint(String executionId, String nodeId) {
        ExecutionResult previousResult = executionResults.get(executionId);
        if (previousResult == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Execution not found: " + executionId)
            );
        }
        
        if (checkpointManager == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Checkpoint manager not configured")
            );
        }
        
        GraphState checkpointState = checkpointManager.restoreCheckpoint(executionId, nodeId);
        if (checkpointState == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Checkpoint not found: " + nodeId)
            );
        }
        
        return execute(previousResult.getWorkflowId(), checkpointState);
    }
    
    /**
     * 获取执行结果
     */
    public ExecutionResult getExecutionResult(String executionId) {
        return executionResults.get(executionId);
    }
    
    /**
     * 构建执行上下文
     */
    private ExecutionContext buildExecutionContext(String executionId, String workflowId, ExecutionConfig config) {
        ExecutionContext.ExecutionContextBuilder builder = ExecutionContext.builder()
            .executionId(executionId)
            .workflowId(workflowId)
            .vertx(vertx);
        
        if (checkpointManager != null) {
            builder.checkpointManager(checkpointManager);
        }
        
        if (llmClient != null) {
            builder.llmClient(llmClient);
        }
        
        if (embeddingClient != null) {
            builder.embeddingClient(embeddingClient);
        }
        
        if (knowledgeBase != null) {
            builder.knowledgeBase(knowledgeBase);
        }
        
        if (config != null && config.getVariables() != null) {
            builder.variables(config.getVariables());
        }
        
        return builder.build();
    }
    
    /**
     * 生成执行ID
     */
    private String generateExecutionId() {
        return "exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    // Setters for dependencies
    public void setCheckpointManager(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }
    
    public void setRepository(WorkflowRepository repository) {
        this.repository = repository;
    }
    
    public void setLlmClient(LLMClient llmClient) {
        this.llmClient = llmClient;
    }
    
    public void setEmbeddingClient(EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }
    
    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }
    
    // Getters for dependencies
    public EmbeddingClient getEmbeddingClient() {
        return embeddingClient;
    }
    
    public LLMClient getLlmClient() {
        return llmClient;
    }
    
    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }
    
    /**
     * 关闭引擎
     */
    public void close() {
        vertx.close();
        if (llmClient != null) {
            try {
                llmClient.close();
            } catch (Exception e) {
                log.error("Failed to close LLM client", e);
            }
        }
        if (embeddingClient != null) {
            try {
                embeddingClient.close();
            } catch (Exception e) {
                log.error("Failed to close embedding client", e);
            }
        }
    }
    
    // 执行配置
    @Data
    @Builder
    public static class ExecutionConfig {
        private Map<String, Object> variables;
        private Integer timeout;
        private Boolean enableCheckpoint;
    }
    
    // 执行结果
    @Data
    @Builder
    public static class ExecutionResult {
        private String executionId;
        private String workflowId;
        private boolean success;
        private GraphState output;
        private String error;
        private long startTime;
        private long endTime;
        private long duration;
    }
}
