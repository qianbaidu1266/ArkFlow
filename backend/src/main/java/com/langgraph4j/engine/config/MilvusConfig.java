package com.langgraph4j.engine.config;

import java.util.Properties;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MilvusConfig {

    private boolean enabled;
    private String host;
    private int port;
    private String token;
    private String dbName;
    private boolean enableBM25;
    private boolean enableReranker;

    public boolean isValid() {
        return enabled && host != null && !host.isEmpty() && port > 0;
    }

    public static MilvusConfig fromProperties(Properties props) {
        return MilvusConfig.builder()
                .enabled(Boolean.parseBoolean(props.getProperty("milvus.enabled", "false")))
                .host(props.getProperty("milvus.host", "localhost"))
                .port(Integer.parseInt(props.getProperty("milvus.port", "19530")))
                .token(props.getProperty("milvus.token", ""))
                .dbName(props.getProperty("milvus.dbName", "default"))
                .enableBM25(Boolean.parseBoolean(props.getProperty("milvus.enableBM25", "true")))
                .enableReranker(Boolean.parseBoolean(props.getProperty("milvus.enableReranker", "false")))
                .build();
    }
}
