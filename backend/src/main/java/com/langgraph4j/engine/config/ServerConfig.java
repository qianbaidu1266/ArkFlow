package com.langgraph4j.engine.config;

import java.util.Properties;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServerConfig {
    
    @Builder.Default
    private int port = 8080;
    
    public static ServerConfig fromProperties(Properties props) {
        return ServerConfig.builder()
            .port(Integer.parseInt(props.getProperty("server.port", "8080")))
            .build();
    }
}
