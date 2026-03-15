package com.langgraph4j.engine;

import com.langgraph4j.engine.api.WorkflowApi;
import com.langgraph4j.engine.core.WorkflowEngine;
import com.langgraph4j.engine.model.LLMClient;
import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.rag.PGVectorKnowledgeBase;
import com.langgraph4j.engine.repository.WorkflowRepository;
import com.langgraph4j.engine.state.RedisCheckpointManager;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * LangGraph4J 应用入口
 */
@Slf4j
public class LangGraph4JApplication {
    
    public static void main(String[] args) {
        log.info("Starting LangGraph4J Engine...");
        
        // 加载配置
        Properties config = loadConfig();
        
        // 创建工作流引擎
        WorkflowEngine engine = new WorkflowEngine();
        
        // 配置数据库存储（工作流持久化 - MySQL）
        String mysqlEnabled = config.getProperty("mysql.enabled", "false");
        String mysqlUrl = config.getProperty("mysql.url", "jdbc:mysql://localhost:3306/langgraph4j");
        String mysqlUser = config.getProperty("mysql.user", "root");
        String mysqlPassword = config.getProperty("mysql.password", "");
        
        if ("true".equalsIgnoreCase(mysqlEnabled) && !mysqlPassword.isEmpty()) {
            try {
                WorkflowRepository repository = new WorkflowRepository(mysqlUrl, mysqlUser, mysqlPassword);
                engine.setRepository(repository);
                log.info("MySQL repository configured: {}", mysqlUrl);
            } catch (Exception e) {
                log.warn("Failed to configure MySQL repository: {}. Workflow persistence will be disabled.", e.getMessage());
            }
        } else {
            log.info("MySQL repository not configured (set mysql.enabled=true and mysql.password to enable workflow persistence)");
        }
        
        // 配置检查点管理器
        String redisUri = config.getProperty("redis.uri", "redis://localhost:6379");
        try {
            RedisCheckpointManager checkpointManager = new RedisCheckpointManager(redisUri);
            engine.setCheckpointManager(checkpointManager);
            log.info("Redis checkpoint manager configured");
        } catch (Exception e) {
            log.warn("Failed to configure Redis checkpoint manager: {}. Checkpoint features will be disabled.", e.getMessage());
        }
        
        // 配置LLM客户端
        String llmBaseUrl = config.getProperty("llm.baseUrl", "https://api.openai.com/v1");
        String llmApiKey = config.getProperty("llm.apiKey", "");
        String llmModel = config.getProperty("llm.model", "gpt-3.5-turbo");
        
        if (!llmApiKey.isEmpty()) {
            LLMClient llmClient = new LLMClient(llmBaseUrl, llmApiKey, llmModel, null);
            engine.setLlmClient(llmClient);
            log.info("LLM client configured: {}", llmModel);
        }
        
        // 配置Embedding客户端
        String embeddingBaseUrl = config.getProperty("embedding.baseUrl", llmBaseUrl);
        String embeddingApiKey = config.getProperty("embedding.apiKey", llmApiKey);
        String embeddingModel = config.getProperty("embedding.model", "text-embedding-3-small");
        int embeddingDimensions = Integer.parseInt(config.getProperty("embedding.dimensions", "1536"));
        
        if (!embeddingApiKey.isEmpty()) {
            EmbeddingClient embeddingClient = new EmbeddingClient(
                embeddingBaseUrl, embeddingApiKey, embeddingModel, embeddingDimensions, null);
            engine.setEmbeddingClient(embeddingClient);
            log.info("Embedding client configured: {}", embeddingModel);
        }
        
        // 配置知识库（可选）
        String pgUrl = config.getProperty("pgvector.url", "jdbc:postgresql://localhost:5432/langgraph4j");
        String pgUser = config.getProperty("pgvector.user", "postgres");
        String pgPassword = config.getProperty("pgvector.password", "");
        String pgEnabled = config.getProperty("pgvector.enabled", "false");
        
        if ("true".equalsIgnoreCase(pgEnabled) && !pgPassword.isEmpty() && engine.getEmbeddingClient() != null) {
            try {
                PGVectorKnowledgeBase knowledgeBase = new PGVectorKnowledgeBase(
                    "default", "Default Knowledge Base",
                    pgUrl, pgUser, pgPassword,
                    engine.getEmbeddingClient(),
                    null,
                    embeddingDimensions
                );
                engine.setKnowledgeBase(knowledgeBase);
                log.info("Knowledge base configured");
            } catch (Exception e) {
                log.warn("Failed to configure knowledge base: {}. Knowledge base features will be disabled.", e.getMessage());
            }
        } else {
            log.info("Knowledge base not configured (set pgvector.enabled=true and pgvector.password to enable)");
        }
        
        // 启动API服务
        int port = Integer.parseInt(config.getProperty("server.port", "8080"));
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new WorkflowApi(engine, port));
        
        log.info("LangGraph4J Engine started on port {}", port);
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down LangGraph4J Engine...");
            engine.close();
            vertx.close();
        }));
    }
    
    private static Properties loadConfig() {
        Properties props = new Properties();
        
        // 从配置文件加载
        try (InputStream is = LangGraph4JApplication.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                log.info("Loaded configuration from application.properties");
            }
        } catch (IOException e) {
            log.warn("Failed to load application.properties, using defaults");
        }
        
        // 从环境变量加载（覆盖配置文件）
        String[] envVars = {
            "REDIS_URI", "LLM_BASE_URL", "LLM_API_KEY", "LLM_MODEL",
            "EMBEDDING_BASE_URL", "EMBEDDING_API_KEY", "EMBEDDING_MODEL", "EMBEDDING_DIMENSIONS",
            "PGVECTOR_URL", "PGVECTOR_USER", "PGVECTOR_PASSWORD", "PGVECTOR_ENABLED",
            "MYSQL_URL", "MYSQL_USER", "MYSQL_PASSWORD", "MYSQL_ENABLED",
            "SERVER_PORT"
        };
        
        for (String envVar : envVars) {
            String value = System.getenv(envVar);
            if (value != null && !value.isEmpty()) {
                String key = envVar.toLowerCase().replace("_", ".");
                props.setProperty(key, value);
            }
        }
        
        return props;
    }
}
