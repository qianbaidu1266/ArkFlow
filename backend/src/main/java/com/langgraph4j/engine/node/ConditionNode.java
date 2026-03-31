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
    protected CompletableFuture<GraphState> doExecute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Condition node: {}", id);
                
                String conditionType = getConfigValue("conditionType", "expression");
                String expression = getConfigValue("expression", "true");
                List<ConditionCase> cases = getConfigValue("cases", null);
                String inputVariable = getConfigValue("inputVariable", null);
                
                String result;
                
                if ("switch".equals(conditionType)) {
                    result = evaluateSwitchCase(state, inputVariable, cases);
                } else {
                    result = evaluateExpression(state, expression) ? "true" : "false";
                }
                
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
    
    @Override
    protected Map<String, Object> extractInputs(GraphState state) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("conditionType", getConfigValue("conditionType", "expression"));
        inputs.put("expression", getConfigValue("expression", "true"));
        
        String inputVariable = getConfigValue("inputVariable", null);
        if (inputVariable != null && state.contains(inputVariable)) {
            inputs.put("inputValue", state.get(inputVariable));
        }
        return inputs;
    }
    
    @Override
    protected Map<String, Object> extractOutputs(GraphState state) {
        Map<String, Object> outputs = new HashMap<>();
        if (state.contains("__condition_result")) {
            outputs.put("result", state.get("__condition_result"));
        }
        return outputs;
    }
    
    private boolean evaluateExpression(GraphState state, String expression) {
        try {
            String renderedExpression = renderVariables(expression, state);
            
            SimpleBindings bindings = new SimpleBindings();
            bindings.putAll(state.getAll());
            
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
    
    @lombok.Data
    public static class ConditionCase {
        private String target;
        private String operator;
        private String value;
        private String description;
    }
}
