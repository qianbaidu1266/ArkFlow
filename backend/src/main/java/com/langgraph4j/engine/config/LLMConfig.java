package com.langgraph4j.engine.config;

import java.util.Properties;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LLMConfig {
    
    private String baseUrl;
    private String apiKey;
    private String model;
    
    @Builder.Default
    private int timeout = 60000;
    
    @Builder.Default
    private int maxRetries = 3;
    
    public boolean isValid() {
        return baseUrl != null && !baseUrl.isEmpty()
            && apiKey != null && !apiKey.isEmpty()
            && model != null && !model.isEmpty();
    }
    
    public static LLMConfig fromProperties(Properties props) {
        return LLMConfig.builder()
            .baseUrl(props.getProperty("llm.baseUrl", "https://api.openai.com/v1"))
            .apiKey(props.getProperty("llm.apiKey", ""))
            .model(props.getProperty("llm.model", "gpt-3.5-turbo"))
            .build();
    }
}
