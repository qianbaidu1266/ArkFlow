package com.langgraph4j.engine.executor;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CodeExecutorFactory {
    
    private static final Map<String, CodeExecutor> executors = new ConcurrentHashMap<>();
    
    static {
        registerExecutor(new JavaScriptExecutor());
        registerExecutor(new PythonExecutor());
        registerExecutor(new JavaExecutor());
    }
    
    public static void registerExecutor(CodeExecutor executor) {
        executors.put(executor.getLanguage().toLowerCase(), executor);
        log.info("Registered code executor for language: {}", executor.getLanguage());
    }
    
    public static CodeExecutor getExecutor(String language) {
        if (language == null || language.isEmpty()) {
            language = "javascript";
        }
        
        CodeExecutor executor = executors.get(language.toLowerCase());
        if (executor == null) {
            log.warn("No executor found for language: {}, falling back to JavaScript", language);
            executor = executors.get("javascript");
        }
        
        return executor;
    }
    
    public static boolean isLanguageSupported(String language) {
        return executors.containsKey(language.toLowerCase());
    }
    
    public static java.util.Set<String> getSupportedLanguages() {
        return executors.keySet();
    }
}
