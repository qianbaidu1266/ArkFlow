package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 开始节点
 * 工作流的入口节点
 */
@Slf4j
public class StartNode extends Node {
    
    public StartNode(String id, String name) {
        super(id, name, NodeType.START);
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Executing Start node: {}", id);
            
            // 获取配置
            List<InputVariable> inputVariables = getConfigValue("inputVariables", List.of());
            
            // 验证必需的输入变量
            for (InputVariable var : inputVariables) {
                if (var.isRequired() && !state.contains(var.getName())) {
                    throw new RuntimeException("Required input variable missing: " + var.getName());
                }
            }
            
            // 设置默认值
            GraphState newState = state.copy();
            for (InputVariable var : inputVariables) {
                if (!newState.contains(var.getName()) && var.getDefaultValue() != null) {
                    newState.set(var.getName(), var.getDefaultValue());
                }
            }
            
            log.debug("Start node executed: {}, input variables: {}", id, inputVariables.size());
            
            return newState;
        });
    }
    
    @Override
    public boolean validate() {
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        List<InputVariable> inputVariables = getConfigValue("inputVariables", List.of());
        for (InputVariable var : inputVariables) {
            ParameterDef def = new ParameterDef();
            def.setName(var.getName());
            def.setType(var.getType());
            def.setDescription(var.getDescription());
            def.setRequired(var.isRequired());
            def.setDefaultValue(var.getDefaultValue());
            params.put(var.getName(), def);
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        List<InputVariable> inputVariables = getConfigValue("inputVariables", List.of());
        for (InputVariable var : inputVariables) {
            ParameterDef def = new ParameterDef();
            def.setName(var.getName());
            def.setType(var.getType());
            def.setDescription(var.getDescription());
            def.setRequired(false);
            params.put(var.getName(), def);
        }
        
        return params;
    }
    
    // 输入变量配置
    @lombok.Data
    public static class InputVariable {
        private String name;
        private String type = "string";
        private String description;
        private boolean required = true;
        private Object defaultValue;
    }
}
