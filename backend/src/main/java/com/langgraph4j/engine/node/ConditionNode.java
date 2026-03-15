package com.langgraph4j.engine.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件分支节点
 * 根据条件表达式决定执行路径
 */
@Slf4j
public class ConditionNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private final ScriptEngine scriptEngine;
    
    public ConditionNode(String id, String name) {
        super(id, name, NodeType.CONDITION);
        ScriptEngineManager manager = new ScriptEngineManager();
        this.scriptEngine = manager.getEngineByName("JavaScript");
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Condition node: {}", id);
                
                // 获取配置
                String conditionType = getConfigValue("conditionType", "expression");
                String expression = getConfigValue("expression", "true");
                List<ConditionCase> cases = getConfigValue("cases", null);
                String inputVariable = getConfigValue("inputVariable", null);
                
                String result;
                
                if ("switch".equals(conditionType)) {
                    // 多分支条件
                    result = evaluateSwitchCase(state, inputVariable, cases);
                } else {
                    // 表达式条件
                    result = evaluateExpression(state, expression) ? "true" : "false";
                }
                
                // 更新状态
                GraphState newState = state.copy();
                newState.set("__condition_result", result);
                newState.set(id + "_result", result);
                
                log.debug("Condition node evaluated: {} = {}", id, result);
                
                return newState;
                
            } catch (Exception e) {
                log.error("Condition node execution failed: {}", id, e);
                throw new RuntimeException("Condition node execution failed", e);
            }
        });
    }
    
    /**
     * 评估表达式
     */
    private boolean evaluateExpression(GraphState state, String expression) {
        try {
            // 替换变量
            String renderedExpression = renderVariables(expression, state);
            
            // 创建绑定
            SimpleBindings bindings = new SimpleBindings();
            bindings.putAll(state.getAll());
            
            // 执行表达式
            Object result = scriptEngine.eval(renderedExpression, bindings);
            
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            
            return Boolean.parseBoolean(result.toString());
            
        } catch (Exception e) {
            log.error("Failed to evaluate expression: {}", expression, e);
            return false;
        }
    }
    
    /**
     * 评估多分支条件
     */
    private String evaluateSwitchCase(GraphState state, String inputVariable, List<ConditionCase> cases) {
        if (inputVariable == null || cases == null || cases.isEmpty()) {
            return "default";
        }
        
        Object inputValue = state.get(inputVariable);
        if (inputValue == null) {
            return "default";
        }
        
        String inputStr = inputValue.toString();
        
        for (ConditionCase conditionCase : cases) {
            if (matchesCase(inputStr, conditionCase, state)) {
                return conditionCase.getTarget();
            }
        }
        
        return "default";
    }
    
    /**
     * 检查是否匹配条件分支
     */
    private boolean matchesCase(String inputValue, ConditionCase conditionCase, GraphState state) {
        String operator = conditionCase.getOperator();
        String value = conditionCase.getValue();
        
        if (operator == null) {
            operator = "equals";
        }
        
        switch (operator) {
            case "equals":
                return inputValue.equals(value);
            case "not_equals":
                return !inputValue.equals(value);
            case "contains":
                return inputValue.contains(value);
            case "not_contains":
                return !inputValue.contains(value);
            case "starts_with":
                return inputValue.startsWith(value);
            case "ends_with":
                return inputValue.endsWith(value);
            case "regex":
                return inputValue.matches(value);
            case "greater_than":
                try {
                    double inputNum = Double.parseDouble(inputValue);
                    double compareNum = Double.parseDouble(value);
                    return inputNum > compareNum;
                } catch (NumberFormatException e) {
                    return false;
                }
            case "less_than":
                try {
                    double inputNum = Double.parseDouble(inputValue);
                    double compareNum = Double.parseDouble(value);
                    return inputNum < compareNum;
                } catch (NumberFormatException e) {
                    return false;
                }
            case "expression":
                return evaluateExpression(state, value);
            default:
                return false;
        }
    }
    
    /**
     * 渲染变量
     */
    private String renderVariables(String template, GraphState state) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        StringBuilder result = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object varValue = state.get(varName);
            String replacement = varValue != null ? "\"" + varValue.toString().replace("\"", "\\\"") + "\"" : "null";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    @Override
    public boolean validate() {
        if (config == null) return false;
        
        String conditionType = getConfigValue("conditionType", "expression");
        
        if ("expression".equals(conditionType)) {
            String expression = getConfigValue("expression", null);
            if (expression == null || expression.isEmpty()) {
                log.error("Condition node {}: expression is required", id);
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String inputVariable = getConfigValue("inputVariable", null);
        if (inputVariable != null) {
            ParameterDef def = new ParameterDef();
            def.setName(inputVariable);
            def.setType("any");
            def.setDescription("Input variable for condition evaluation");
            def.setRequired(true);
            params.put(inputVariable, def);
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        ParameterDef resultDef = new ParameterDef();
        resultDef.setName("__condition_result");
        resultDef.setType("string");
        resultDef.setDescription("Condition evaluation result");
        resultDef.setRequired(true);
        params.put("__condition_result", resultDef);
        
        return params;
    }
    
    // 条件分支配置
    @lombok.Data
    public static class ConditionCase {
        private String target;  // 目标分支标识
        private String operator;  // 操作符
        private String value;  // 比较值
        private String description;  // 描述
    }
}
