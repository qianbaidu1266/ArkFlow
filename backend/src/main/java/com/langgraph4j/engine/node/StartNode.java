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

@Slf4j
public class StartNode extends Node {
    
    public StartNode(String id, String name) {
        super(id, name, NodeType.START);
    }
    
    @Override
    protected CompletableFuture<GraphState> doExecute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Executing Start node: {}", id);
            
            List<InputVariable> inputVariables = getConfigValue("inputVariables", List.of());
            
            for (InputVariable var : inputVariables) {
                if (var.isRequired() && !state.contains(var.getName())) {
                    throw new RuntimeException("Required input variable missing: " + var.getName());
                }
            }
            
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
    protected Map<String, Object> extractInputs(GraphState state) {
        return Map.of("inputVariables", getConfigValue("inputVariables", List.of()));
    }
    
    @Override
    protected Map<String, Object> extractOutputs(GraphState state) {
        return state.getAll();
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
    
    @lombok.Data
    public static class InputVariable {
        private String name;
        private String type = "string";
        private String description;
        private boolean required = true;
        private Object defaultValue;
    }
}
