package com.langgraph4j.engine.executor;

import java.util.Map;

public interface CodeExecutor {
    
    String getLanguage();
    
    Object execute(String code, Map<String, Object> variables, long timeoutMs) throws Exception;
    
    default String wrapError(String message, Throwable e) {
        return String.format("[%s] %s: %s", getLanguage(), message, e.getMessage());
    }
}
