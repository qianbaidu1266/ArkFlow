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

@Slf4j
public class EndNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    
    public EndNode(String id, String name) {
        super(id, name, NodeType.END);
    }
    
    @Override
    protected CompletableFuture<GraphState> doExecute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Executing End node: {}", id);
            
            List<OutputVariable> outputVariables = getConfigValue("outputVariables", List.of());
            String outputFormat = getConfigValue("outputFormat", "object");
            String outputTemplate = getConfigValue("outputTemplate", null);
            
            GraphState newState = state.copy();
            
            if ("text".equals(outputFormat) && outputTemplate != null) {
                String renderedOutput = renderTemplate(outputTemplate, state);
                newState.set("__output", renderedOutput);
            } else {
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
            
            newState.set("__completed", true);
            newState.set("__end_time", System.currentTimeMillis());
            
            log.debug("End node executed: {}", id);
            
            return newState;
        });
    }
    
    @Override
    protected Map<String, Object> extractInputs(GraphState state) {
        return state.getAll();
    }
    
    @Override
    protected Map<String, Object> extractOutputs(GraphState state) {
        Map<String, Object> outputs = new HashMap<>();
        if (state.contains("__output")) {
            outputs.put("output", state.get("__output"));
        }
        return outputs;
    }
    
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
