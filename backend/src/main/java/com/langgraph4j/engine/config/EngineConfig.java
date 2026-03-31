package com.langgraph4j.engine.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Builder
@Slf4j
public class EngineConfig {
    
    private ServerConfig server;
    private DatabaseConfig mysql;
    private DatabaseConfig pgvector;
    private LLMConfig llm;
    private EmbeddingConfig embedding;
    private RedisConfig redis;
    
    public static EngineConfig load() {
        Properties props = loadProperties();
        applyEnvironmentOverrides(props);
        
        return EngineConfig.builder()
            .server(ServerConfig.fromProperties(props))
            .mysql(DatabaseConfig.mysqlFromProperties(props))
            .pgvector(DatabaseConfig.pgvectorFromProperties(props))
            .llm(LLMConfig.fromProperties(props))
            .embedding(EmbeddingConfig.fromProperties(props))
            .redis(RedisConfig.fromProperties(props))
            .build();
    }
    
    private static Properties loadProperties() {
        Properties props = new Properties();
        
        try (InputStream is = EngineConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                log.info("Loaded configuration from application.properties");
            }
        } catch (IOException e) {
            log.warn("Failed to load application.properties, using defaults");
        }
        
        return props;
    }
    
    private static void applyEnvironmentOverrides(Properties props) {
        String[] envMappings = {
            "SERVER_PORT:server.port",
            "MYSQL_URL:mysql.url",
            "MYSQL_USER:mysql.user",
            "MYSQL_PASSWORD:mysql.password",
            "MYSQL_ENABLED:mysql.enabled",
            "PGVECTOR_URL:pgvector.url",
            "PGVECTOR_USER:pgvector.user",
            "PGVECTOR_PASSWORD:pgvector.password",
            "PGVECTOR_ENABLED:pgvector.enabled",
            "REDIS_URI:redis.uri",
            "LLM_BASE_URL:llm.baseUrl",
            "LLM_API_KEY:llm.apiKey",
            "LLM_MODEL:llm.model",
            "EMBEDDING_BASE_URL:embedding.baseUrl",
            "EMBEDDING_API_KEY:embedding.apiKey",
            "EMBEDDING_MODEL:embedding.model",
            "EMBEDDING_DIMENSIONS:embedding.dimensions"
        };
        
        for (String mapping : envMappings) {
            String[] parts = mapping.split(":");
            String envVar = parts[0];
            String propKey = parts[1];
            
            String value = System.getenv(envVar);
            if (value != null && !value.isEmpty()) {
                props.setProperty(propKey, value);
            }
        }
    }
    
    public void validate() {
        if (mysql.isValid()) {
            log.info("MySQL configuration: enabled, url={}", mysql.getUrl());
        } else {
            log.info("MySQL configuration: disabled");
        }
        
        if (pgvector.isValid()) {
            log.info("PGVector configuration: enabled, url={}", pgvector.getUrl());
        } else {
            log.info("PGVector configuration: disabled");
        }
        
        if (llm.isValid()) {
            log.info("LLM configuration: enabled, model={}", llm.getModel());
        } else {
            log.warn("LLM configuration: disabled (set llm.apiKey to enable)");
        }
        
        if (embedding.isValid()) {
            log.info("Embedding configuration: enabled, model={}", embedding.getModel());
        } else {
            log.info("Embedding configuration: disabled");
        }
        
        if (redis.isValid()) {
            log.info("Redis configuration: enabled, uri={}", redis.getUri());
        } else {
            log.info("Redis configuration: disabled");
        }
    }
}
