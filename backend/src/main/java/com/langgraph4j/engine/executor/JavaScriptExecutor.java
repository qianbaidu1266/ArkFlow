package com.langgraph4j.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.Map;

@Slf4j
public class JavaScriptExecutor implements CodeExecutor {
    
    private final ScriptEngine scriptEngine;
    private final ObjectMapper objectMapper;
    
    public JavaScriptExecutor() {
        ScriptEngineManager manager = new ScriptEngineManager();
        this.scriptEngine = manager.getEngineByName("JavaScript");
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public String getLanguage() {
        return "javascript";
    }
    
    @Override
    public Object execute(String code, Map<String, Object> variables, long timeoutMs) throws Exception {
        SimpleBindings bindings = new SimpleBindings();
        
        if (variables != null) {
            bindings.putAll(variables);
        }
        
        bindings.put("console", new Console());
        bindings.put("JSON", new JSONHelper());
        
        Object result = scriptEngine.eval(code, bindings);
        
        return result;
    }
    
    public static class Console {
        public void log(Object... args) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(arg);
            }
            log.info("[JavaScript] {}", sb);
        }
        
        public void error(Object... args) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(arg);
            }
            log.error("[JavaScript] {}", sb);
        }
        
        public void warn(Object... args) {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(arg);
            }
            log.warn("[JavaScript] {}", sb);
        }
    }
    
    public class JSONHelper {
        public String stringify(Object obj) {
            try {
                return objectMapper.writeValueAsString(obj);
            } catch (Exception e) {
                return "{}";
            }
        }
        
        public Object parse(String json) {
            try {
                return objectMapper.readValue(json, Object.class);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
