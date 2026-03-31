package com.langgraph4j.engine.config;

import java.util.Properties;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmbeddingConfig {
    
    private String baseUrl;
    private String apiKey;
    private String model;
    
    @Builder.Default
    private int dimensions = 1536;
    
    @Builder.Default
    private int timeout = 30000;
    
    public boolean isValid() {
        return baseUrl != null && !baseUrl.isEmpty()
            && apiKey != null && !apiKey.isEmpty()
            && model != null && !model.isEmpty();
    }
    
    public static EmbeddingConfig fromProperties(Properties props) {
        String llmBaseUrl = props.getProperty("llm.baseUrl", "https://api.openai.com/v1");
        String llmApiKey = props.getProperty("llm.apiKey", "");
        
        return EmbeddingConfig.builder()
            .baseUrl(props.getProperty("embedding.baseUrl", llmBaseUrl))
            .apiKey(props.getProperty("embedding.apiKey", llmApiKey))
            .model(props.getProperty("embedding.model", "text-embedding-3-small"))
            .dimensions(Integer.parseInt(props.getProperty("embedding.dimensions", "1536")))
            .build();
    }
}
