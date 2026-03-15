package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 代码执行节点
 * 执行JavaScript/Python代码
 */
@Slf4j
public class CodeNode extends Node {
    
    private final ScriptEngine scriptEngine;
    
    public CodeNode(String id, String name) {
        super(id, name, NodeType.CODE);
        ScriptEngineManager manager = new ScriptEngineManager();
        this.scriptEngine = manager.getEngineByName("JavaScript");
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Code node: {}", id);
                
                // 获取配置
                String language = getConfigValue("language", "javascript");
                String code = getConfigValue("code", "");
                String outputKey = getConfigValue("outputKey", "code_result");
                Map<String, String> inputMappings = getConfigValue("inputMappings", new HashMap<>());
                
                // 创建绑定
                SimpleBindings bindings = new SimpleBindings();
                
                // 映射输入变量
                for (Map.Entry<String, String> entry : inputMappings.entrySet()) {
                    String varName = entry.getKey();
                    String stateKey = entry.getValue();
                    Object value = state.get(stateKey);
                    bindings.put(varName, value);
                }
                
                // 添加所有状态变量
                bindings.putAll(state.getAll());
                
                // 添加工具函数
                bindings.put("console", new Console());
                bindings.put("JSON", new JSON());
                
                // 执行代码
                Object result = scriptEngine.eval(code, bindings);
                
                // 更新状态
                GraphState newState = state.copy();
                newState.set(outputKey, result);
                
                // 获取输出变量
                Map<String, String> outputMappings = getConfigValue("outputMappings", new HashMap<>());
                for (Map.Entry<String, String> entry : outputMappings.entrySet()) {
                    String varName = entry.getKey();
                    String stateKey = entry.getValue();
                    Object value = bindings.get(varName);
                    if (value != null) {
                        newState.set(stateKey, value);
                    }
                }
                
                log.debug("Code node executed successfully: {}", id);
                
                return newState;
                
            } catch (Exception e) {
                log.error("Code node execution failed: {}", id, e);
                throw new RuntimeException("Code node execution failed", e);
            }
        });
    }
    
    @Override
    public boolean validate() {
        if (config == null) return false;
        
        String code = getConfigValue("code", null);
        if (code == null || code.isEmpty()) {
            log.error("Code node {}: code is required", id);
            return false;
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
    
    // 控制台工具类
    public static class Console {
        public void log(Object... args) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(arg);
            }
            log.info("[CodeNode] {}", sb);
        }
        
        public void error(Object... args) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(arg);
            }
            log.error("[CodeNode] {}", sb);
        }
    }
    
    // JSON工具类
    public static class JSON {
        public String stringify(Object obj) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
            } catch (Exception e) {
                return "{}";
            }
        }
        
        public Object parse(String json) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
