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
 * 变量赋值节点
 * 设置或修改变量值
 */
@Slf4j
public class VariableAssignerNode extends Node {
    
    public VariableAssignerNode(String id, String name) {
        super(id, name, NodeType.VARIABLE_ASSIGNER);
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Variable Assigner node: {}", id);
                
                // 获取配置
                List<VariableAssignment> assignments = getConfigValue("assignments", List.of());
                
                // 执行赋值
                GraphState newState = state.copy();
                for (VariableAssignment assignment : assignments) {
                    Object value = evaluateValue(assignment, state);
                    newState.set(assignment.getTargetVariable(), value);
                    log.debug("Variable assigned: {} = {}", assignment.getTargetVariable(), value);
                }
                
                log.debug("Variable Assigner node executed successfully: {}", id);
                
                return newState;
                
            } catch (Exception e) {
                log.error("Variable Assigner node execution failed: {}", id, e);
                throw new RuntimeException("Variable Assigner node execution failed", e);
            }
        });
    }
    
    /**
     * 评估值
     */
    private Object evaluateValue(VariableAssignment assignment, GraphState state) {
        String valueType = assignment.getValueType();
        
        switch (valueType) {
            case "constant":
                return assignment.getValue();
                
            case "variable":
                return state.get(assignment.getSourceVariable());
                
            case "expression":
                return evaluateExpression(assignment.getExpression(), state);
                
            case "concat":
                return concatenateValues(assignment.getConcatItems(), state);
                
            case "transform":
                return transformValue(assignment.getSourceVariable(), assignment.getTransformType(), state);
                
            default:
                return assignment.getValue();
        }
    }
    
    /**
     * 评估表达式
     */
    private Object evaluateExpression(String expression, GraphState state) {
        // 简单的表达式评估
        // 实际实现可以使用脚本引擎
        try {
            javax.script.ScriptEngine engine = new javax.script.ScriptEngineManager().getEngineByName("JavaScript");
            javax.script.SimpleBindings bindings = new javax.script.SimpleBindings();
            bindings.putAll(state.getAll());
            return engine.eval(expression, bindings);
        } catch (Exception e) {
            log.warn("Failed to evaluate expression: {}", expression, e);
            return null;
        }
    }
    
    /**
     * 连接多个值
     */
    private String concatenateValues(List<ConcatItem> items, GraphState state) {
        StringBuilder result = new StringBuilder();
        for (ConcatItem item : items) {
            if ("variable".equals(item.getType())) {
                Object value = state.get(item.getValue());
                if (value != null) {
                    result.append(value);
                }
            } else {
                result.append(item.getValue());
            }
        }
        return result.toString();
    }
    
    /**
     * 转换值
     */
    private Object transformValue(String sourceVariable, String transformType, GraphState state) {
        Object value = state.get(sourceVariable);
        if (value == null) return null;
        
        switch (transformType) {
            case "uppercase":
                return value.toString().toUpperCase();
            case "lowercase":
                return value.toString().toLowerCase();
            case "trim":
                return value.toString().trim();
            case "length":
                return value.toString().length();
            case "json_stringify":
                try {
                    return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
                } catch (Exception e) {
                    return value.toString();
                }
            case "json_parse":
                try {
                    return new com.fasterxml.jackson.databind.ObjectMapper().readValue(value.toString(), Object.class);
                } catch (Exception e) {
                    return value;
                }
            case "to_number":
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    return 0;
                }
            case "to_string":
                return value.toString();
            default:
                return value;
        }
    }
    
    @Override
    public boolean validate() {
        if (config == null) return false;
        
        List<VariableAssignment> assignments = getConfigValue("assignments", null);
        if (assignments == null || assignments.isEmpty()) {
            log.error("Variable Assigner node {}: at least one assignment is required", id);
            return false;
        }
        
        for (VariableAssignment assignment : assignments) {
            if (assignment.getTargetVariable() == null || assignment.getTargetVariable().isEmpty()) {
                log.error("Variable Assigner node {}: targetVariable is required", id);
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        List<VariableAssignment> assignments = getConfigValue("assignments", List.of());
        for (VariableAssignment assignment : assignments) {
            if ("variable".equals(assignment.getValueType())) {
                String sourceVar = assignment.getSourceVariable();
                if (sourceVar != null && !params.containsKey(sourceVar)) {
                    ParameterDef def = new ParameterDef();
                    def.setName(sourceVar);
                    def.setType("any");
                    def.setDescription("Source variable");
                    def.setRequired(true);
                    params.put(sourceVar, def);
                }
            }
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        List<VariableAssignment> assignments = getConfigValue("assignments", List.of());
        for (VariableAssignment assignment : assignments) {
            ParameterDef def = new ParameterDef();
            def.setName(assignment.getTargetVariable());
            def.setType("any");
            def.setDescription("Assigned variable");
            def.setRequired(true);
            params.put(assignment.getTargetVariable(), def);
        }
        
        return params;
    }
    
    // 变量赋值配置
    @lombok.Data
    public static class VariableAssignment {
        private String targetVariable;
        private String valueType = "constant";  // constant, variable, expression, concat, transform
        private Object value;  // for constant
        private String sourceVariable;  // for variable, transform
        private String expression;  // for expression
        private List<ConcatItem> concatItems;  // for concat
        private String transformType;  // for transform
    }
    
    // 连接项配置
    @lombok.Data
    public static class ConcatItem {
        private String type;  // constant, variable
        private String value;
    }
}
