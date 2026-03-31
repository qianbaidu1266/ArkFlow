package com.langgraph4j.engine.config;

import java.util.Properties;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RedisConfig {
    
    private String uri;
    
    @Builder.Default
    private int maxConnections = 10;
    
    @Builder.Default
    private long timeout = 5000;
    
    public boolean isValid() {
        return uri != null && !uri.isEmpty();
    }
    
    public static RedisConfig fromProperties(Properties props) {
        return RedisConfig.builder()
            .uri(props.getProperty("redis.uri", "redis://localhost:6379"))
            .build();
    }
}
