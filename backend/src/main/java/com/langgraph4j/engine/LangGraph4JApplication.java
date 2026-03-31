package com.langgraph4j.engine;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.langgraph4j.engine.api.WorkflowApi;
import com.langgraph4j.engine.config.EngineConfig;
import com.langgraph4j.engine.core.WorkflowEngine;
import com.langgraph4j.engine.di.ConfigModule;
import com.langgraph4j.engine.di.DatabaseModule;
import com.langgraph4j.engine.di.EngineModule;
import com.langgraph4j.engine.di.LLMModule;
import com.langgraph4j.engine.di.RedisModule;
import com.langgraph4j.engine.websocket.ExecutionEventBus;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LangGraph4JApplication {
    
    private final Injector injector;
    private final EngineConfig config;
    
    public LangGraph4JApplication() {
        log.info("Starting LangGraph4J Engine...");
        
        this.config = EngineConfig.load();
        this.config.validate();
        
        this.injector = Guice.createInjector(
            new ConfigModule(config),
            new DatabaseModule(),
            new LLMModule(),
            new RedisModule(),
            new EngineModule()
        );
    }
    
    public void start() {
        WorkflowApi workflowApi = injector.getInstance(WorkflowApi.class);
        Vertx vertx = injector.getInstance(Vertx.class);
        WorkflowEngine engine = injector.getInstance(WorkflowEngine.class);
        
        vertx.deployVerticle(workflowApi)
            .onSuccess(id -> {
                log.info("LangGraph4J Engine started on port {}", config.getServer().getPort());
            })
            .onFailure(err -> {
                log.error("Failed to start LangGraph4J Engine", err);
                System.exit(1);
            });
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down LangGraph4J Engine...");
            engine.close();
            vertx.close();
        }));
    }
    
    public static void main(String[] args) {
        LangGraph4JApplication app = new LangGraph4JApplication();
        app.start();
    }
}
