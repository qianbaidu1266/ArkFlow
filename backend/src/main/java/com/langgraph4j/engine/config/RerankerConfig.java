package com.langgraph4j.engine.config;

import java.util.Properties;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RerankerConfig {

    private String baseUrl;
    private String apiKey;
    private String model;

    public boolean isValid() {
        return baseUrl != null && !baseUrl.isEmpty()
                && model != null && !model.isEmpty();
    }

    public static RerankerConfig fromProperties(Properties props) {
        return RerankerConfig.builder()
                .baseUrl(props.getProperty("reranker.baseUrl", "http://localhost:11434/v1"))
                .apiKey(props.getProperty("reranker.apiKey", "ollama"))
                .model(props.getProperty("reranker.model", "bge-reranker-v2-m3"))
                .build();
    }
}
