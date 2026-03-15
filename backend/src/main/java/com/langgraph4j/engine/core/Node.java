package com.langgraph4j.engine.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.langgraph4j.engine.state.GraphState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工作流节点基类
 * 所有节点类型都需要继承此类
 */
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
    
    /**
     * 执行节点逻辑
     * @param state 当前状态
     * @param context 执行上下文
     * @return 执行后的状态
     */
    public abstract CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context);
    
    /**
     * 验证节点配置
     */
    public abstract boolean validate();
    
    /**
     * 获取输入参数定义
     */
    public abstract Map<String, ParameterDef> getInputParameters();
    
    /**
     * 获取输出参数定义
     */
    public abstract Map<String, ParameterDef> getOutputParameters();
    
    /**
     * 获取配置JSON Schema
     */
    public JsonNode getConfigSchema() {
        return null;
    }
    
    /**
     * 更新配置
     */
    public void updateConfig(Map<String, Object> newConfig) {
        if (this.config != null) {
            this.config.putAll(newConfig);
        } else {
            this.config = newConfig;
        }
    }
    
    /**
     * 获取配置值
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, T defaultValue) {
        if (config == null) return defaultValue;
        Object value = config.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 节点位置
     */
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
    
    /**
     * 参数定义
     */
    @Data
    public static class ParameterDef {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
    }
}
