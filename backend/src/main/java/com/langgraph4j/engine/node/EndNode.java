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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结束节点
 * 工作流的出口节点
 */
@Slf4j
public class EndNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    
    public EndNode(String id, String name) {
        super(id, name, NodeType.END);
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Executing End node: {}", id);
            
            // 获取配置
            List<OutputVariable> outputVariables = getConfigValue("outputVariables", List.of());
            String outputFormat = getConfigValue("outputFormat", "object");  // object, text
            String outputTemplate = getConfigValue("outputTemplate", null);
            
            GraphState newState = state.copy();
            
            if ("text".equals(outputFormat) && outputTemplate != null) {
                // 文本格式输出
                String renderedOutput = renderTemplate(outputTemplate, state);
                newState.set("__output", renderedOutput);
            } else {
                // 对象格式输出
                Map<String, Object> output = new HashMap<>();
                for (OutputVariable var : outputVariables) {
                    Object value = state.get(var.getSourceVariable());
                    if (value != null) {
                        output.put(var.getName(), value);
                    } else if (var.getDefaultValue() != null) {
                        output.put(var.getName(), var.getDefaultValue());
                    }
                }
                newState.set("__output", output);
            }
            
            // 标记执行完成
            newState.set("__completed", true);
            newState.set("__end_time", System.currentTimeMillis());
            
            log.debug("End node executed: {}", id);
            
            return newState;
        });
    }
    
    /**
     * 渲染模板
     */
    private String renderTemplate(String template, GraphState state) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        StringBuilder result = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object varValue = state.get(varName);
            String replacement = varValue != null ? varValue.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    @Override
    public boolean validate() {
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        List<OutputVariable> outputVariables = getConfigValue("outputVariables", List.of());
        for (OutputVariable var : outputVariables) {
            ParameterDef def = new ParameterDef();
            def.setName(var.getSourceVariable());
            def.setType(var.getType());
            def.setDescription("Source variable for output: " + var.getName());
            def.setRequired(var.isRequired());
            params.put(var.getSourceVariable(), def);
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        ParameterDef outputDef = new ParameterDef();
        outputDef.setName("__output");
        outputDef.setType("any");
        outputDef.setDescription("Final output");
        outputDef.setRequired(true);
        params.put("__output", outputDef);
        
        return params;
    }
    
    // 输出变量配置
    @lombok.Data
    public static class OutputVariable {
        private String name;
        private String sourceVariable;
        private String type = "string";
        private String description;
        private boolean required = false;
        private Object defaultValue;
    }
}
