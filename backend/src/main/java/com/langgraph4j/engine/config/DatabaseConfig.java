package com.langgraph4j.engine.config;

import java.util.Properties;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DatabaseConfig {
    
    private boolean enabled;
    private String url;
    private String username;
    private String password;
    
    @Builder.Default
    private int maxPoolSize = 10;
    
    @Builder.Default
    private long connectionTimeout = 30000;
    
    public boolean isValid() {
        return enabled && url != null && !url.isEmpty() 
            && username != null && !username.isEmpty()
            && password != null && !password.isEmpty();
    }
    
    public static DatabaseConfig mysqlFromProperties(Properties props) {
        return DatabaseConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("mysql.enabled", "false")))
            .url(props.getProperty("mysql.url", "jdbc:mysql://localhost:3306/langgraph4j"))
            .username(props.getProperty("mysql.user", "root"))
            .password(props.getProperty("mysql.password", ""))
            .build();
    }
    
    public static DatabaseConfig pgvectorFromProperties(Properties props) {
        return DatabaseConfig.builder()
            .enabled(Boolean.parseBoolean(props.getProperty("pgvector.enabled", "false")))
            .url(props.getProperty("pgvector.url", "jdbc:postgresql://localhost:5432/langgraph4j"))
            .username(props.getProperty("pgvector.user", "postgres"))
            .password(props.getProperty("pgvector.password", ""))
            .build();
    }
}
