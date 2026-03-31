package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.executor.CodeExecutor;
import com.langgraph4j.engine.executor.CodeExecutorFactory;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CodeNode extends Node {
    
    private static final long DEFAULT_TIMEOUT_MS = 60000;
    
    public CodeNode(String id, String name) {
        super(id, name, NodeType.CODE);
    }
    
    @Override
    protected CompletableFuture<GraphState> doExecute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Code node: {}", id);
                
                String language = getConfigValue("language", "javascript");
                String code = getConfigValue("code", "");
                String outputKey = getConfigValue("outputKey", "code_result");
                long timeout = getConfigValue("timeout", DEFAULT_TIMEOUT_MS);
                Map<String, String> inputMappings = getConfigValue("inputMappings", new HashMap<>());
                
                Map<String, Object> variables = new HashMap<>();
                
                for (Map.Entry<String, String> entry : inputMappings.entrySet()) {
                    String varName = entry.getKey();
                    String stateKey = entry.getValue();
                    Object value = state.get(stateKey);
                    variables.put(varName, value);
                }
                
                variables.putAll(state.getAll());
                
                CodeExecutor executor = CodeExecutorFactory.getExecutor(language);
                log.info("Executing {} code with {} executor", language, executor.getClass().getSimpleName());
                
                Object result = executor.execute(code, variables, timeout);
                
                GraphState newState = state.copy();
                newState.set(outputKey, result);
                
                Map<String, String> outputMappings = getConfigValue("outputMappings", new HashMap<>());
                for (Map.Entry<String, String> entry : outputMappings.entrySet()) {
                    String varName = entry.getKey();
                    String stateKey = entry.getValue();
                    if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultMap = (Map<String, Object>) result;
                        Object value = resultMap.get(varName);
                        if (value != null) {
                            newState.set(stateKey, value);
                        }
                    }
                }
                
                log.debug("Code node executed successfully: {}", id);
                
                return newState;
                
            } catch (Exception e) {
                log.error("Code node execution failed: {}", id, e);
                throw new RuntimeException("Code node execution failed: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    protected Map<String, Object> extractInputs(GraphState state) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("language", getConfigValue("language", "javascript"));
        inputs.put("code", getConfigValue("code", ""));
        
        Map<String, String> inputMappings = getConfigValue("inputMappings", new HashMap<>());
        for (Map.Entry<String, String> entry : inputMappings.entrySet()) {
            if (state.contains(entry.getValue())) {
                inputs.put(entry.getKey(), state.get(entry.getValue()));
            }
        }
        return inputs;
    }
    
    @Override
    protected Map<String, Object> extractOutputs(GraphState state) {
        Map<String, Object> outputs = new HashMap<>();
        String outputKey = getConfigValue("outputKey", "code_result");
        if (state.contains(outputKey)) {
            outputs.put("result", state.get(outputKey));
        }
        return outputs;
    }
    
    @Override
    public boolean validate() {
        if (config == null) return false;
        
        String code = getConfigValue("code", null);
        if (code == null || code.isEmpty()) {
            log.error("Code node {}: code is required", id);
            return false;
        }
        
        String language = getConfigValue("language", "javascript");
        if (!CodeExecutorFactory.isLanguageSupported(language)) {
            log.warn("Code node {}: unsupported language {}, will use fallback", id, language);
        }
        
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        Map<String, String> inputMappings = getConfigValue("inputMappings", new HashMap<>());
        for (Map.Entry<String, String> entry : inputMappings.entrySet()) {
            ParameterDef def = new ParameterDef();
            def.setName(entry.getValue());
            def.setType("any");
            def.setDescription("Input: " + entry.getKey());
            def.setRequired(true);
            params.put(entry.getValue(), def);
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String outputKey = getConfigValue("outputKey", "code_result");
        
        ParameterDef resultDef = new ParameterDef();
        resultDef.setName(outputKey);
        resultDef.setType("any");
        resultDef.setDescription("Code execution result");
        resultDef.setRequired(true);
        params.put(outputKey, resultDef);
        
        Map<String, String> outputMappings = getConfigValue("outputMappings", new HashMap<>());
        for (Map.Entry<String, String> entry : outputMappings.entrySet()) {
            ParameterDef def = new ParameterDef();
            def.setName(entry.getValue());
            def.setType("any");
            def.setDescription("Output: " + entry.getKey());
            def.setRequired(false);
            params.put(entry.getValue(), def);
        }
        
        return params;
    }
}
