package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.executor.CodeExecutor;
import com.langgraph4j.engine.executor.CodeExecutorFactory;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
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
                long timeout = getConfigValue("timeout", DEFAULT_TIMEOUT_MS);
                
                // 获取输入变量配置
                List<Map<String, Object>> inputVariables = getConfigList("inputVariables");
                List<Map<String, Object>> outputVariables = getConfigList("outputVariables");
                
                // 构建输入参数
                Map<String, Object> variables = new LinkedHashMap<>();
                for (Map<String, Object> varDef : inputVariables) {
                    String varName = (String) varDef.get("name");
                    String sourceKey = (String) varDef.get("source");
                    if (varName != null && sourceKey != null) {
                        Object value = state.get(sourceKey);
                        variables.put(varName, value);
                    }
                }
                
                // 添加所有状态变量作为备选
                variables.putAll(state.getAll());
                
                CodeExecutor executor = CodeExecutorFactory.getExecutor(language);
                log.info("Executing {} code with {} executor", language, executor.getClass().getSimpleName());
                
                Object result = executor.execute(code, variables, timeout);
                
                GraphState newState = state.copy();
                
                // 处理输出变量
                if (result instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) result;
                    
                    // 根据 outputVariables 配置提取输出
                    for (Map<String, Object> varDef : outputVariables) {
                        String varName = (String) varDef.get("name");
                        String targetKey = (String) varDef.get("target");
                        if (varName != null && targetKey != null) {
                            Object value = resultMap.get(varName);
                            newState.set(targetKey, value);
                        }
                    }
                    
                    // 如果没有配置输出变量，将整个结果存入默认键
                    if (outputVariables.isEmpty()) {
                        newState.set("code_result", result);
                    }
                } else {
                    // 非对象结果，存入默认键
                    newState.set("code_result", result);
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
        
        List<Map<String, Object>> inputVariables = getConfigList("inputVariables");
        for (Map<String, Object> varDef : inputVariables) {
            String sourceKey = (String) varDef.get("source");
            if (sourceKey != null && state.contains(sourceKey)) {
                inputs.put(sourceKey, state.get(sourceKey));
            }
        }
        return inputs;
    }
    
    @Override
    protected Map<String, Object> extractOutputs(GraphState state) {
        Map<String, Object> outputs = new HashMap<>();
        
        List<Map<String, Object>> outputVariables = getConfigList("outputVariables");
        for (Map<String, Object> varDef : outputVariables) {
            String targetKey = (String) varDef.get("target");
            if (targetKey != null && state.contains(targetKey)) {
                outputs.put(targetKey, state.get(targetKey));
            }
        }
        
        // 如果没有配置输出变量，检查默认键
        if (outputs.isEmpty() && state.contains("code_result")) {
            outputs.put("code_result", state.get("code_result"));
        }
        
        return outputs;
    }
    
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getConfigList(String key) {
        Object value = config != null ? config.get(key) : null;
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
        }
        return result;
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
        
        List<Map<String, Object>> inputVariables = getConfigList("inputVariables");
        for (Map<String, Object> varDef : inputVariables) {
            String sourceKey = (String) varDef.get("source");
            if (sourceKey != null) {
                ParameterDef def = new ParameterDef();
                def.setName(sourceKey);
                def.setType("any");
                def.setDescription("Input: " + varDef.get("name"));
                def.setRequired(true);
                params.put(sourceKey, def);
            }
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        List<Map<String, Object>> outputVariables = getConfigList("outputVariables");
        for (Map<String, Object> varDef : outputVariables) {
            String targetKey = (String) varDef.get("target");
            if (targetKey != null) {
                ParameterDef def = new ParameterDef();
                def.setName(targetKey);
                def.setType("any");
                def.setDescription("Output: " + varDef.get("name"));
                def.setRequired(false);
                params.put(targetKey, def);
            }
        }
        
        // 如果没有配置输出变量，添加默认输出
        if (params.isEmpty()) {
            ParameterDef def = new ParameterDef();
            def.setName("code_result");
            def.setType("any");
            def.setDescription("Code execution result");
            def.setRequired(true);
            params.put("code_result", def);
        }
        
        return params;
    }
}
