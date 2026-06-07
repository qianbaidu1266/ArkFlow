package com.langgraph4j.engine.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.langgraph4j.engine.api.KnowledgeApi;
import com.langgraph4j.engine.api.WorkflowApi;
import com.langgraph4j.engine.config.ServerConfig;
import com.langgraph4j.engine.core.WorkflowEngine;
import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.model.LLMClient;
import com.langgraph4j.engine.rag.KnowledgeBase;
import com.langgraph4j.engine.rag.KnowledgeBaseManager;
import com.langgraph4j.engine.repository.KnowledgeBaseRepository;
import com.langgraph4j.engine.repository.WorkflowRepository;
import com.langgraph4j.engine.state.CheckpointManager;
import com.langgraph4j.engine.state.SnapshotManager;
import com.langgraph4j.engine.websocket.ExecutionEventBus;
import io.vertx.core.Vertx;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EngineModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(Vertx.class).toInstance(Vertx.vertx());
        bind(ExecutionEventBus.class).toProvider(ExecutionEventBusProvider.class).in(Singleton.class);
        bind(WorkflowEngine.class).toProvider(WorkflowEngineProvider.class).in(Singleton.class);
        bind(WorkflowApi.class).toProvider(WorkflowApiProvider.class).in(Singleton.class);
    }
    
    @Singleton
    static class ExecutionEventBusProvider implements Provider<ExecutionEventBus> {
        private final Vertx vertx;
        
        @com.google.inject.Inject
        public ExecutionEventBusProvider(Vertx vertx) {
            this.vertx = vertx;
        }
        
        @Override
        public ExecutionEventBus get() {
            log.info("Initializing WebSocket event bus");
            return new ExecutionEventBus(vertx);
        }
    }
    
    @Singleton
    static class WorkflowEngineProvider implements Provider<WorkflowEngine> {
        private final WorkflowRepository repository;
        private final SnapshotManager snapshotManager;
        private final CheckpointManager checkpointManager;
        private final LLMClient llmClient;
        private final EmbeddingClient embeddingClient;
        private final KnowledgeBase knowledgeBase;
        private final KnowledgeBaseManager kbManager;
        private final ExecutionEventBus eventBus;
        
        @com.google.inject.Inject
        public WorkflowEngineProvider(
                @Nullable WorkflowRepository repository,
                @Nullable SnapshotManager snapshotManager,
                @Nullable CheckpointManager checkpointManager,
                @Nullable LLMClient llmClient,
                @Nullable EmbeddingClient embeddingClient,
                @Nullable KnowledgeBase knowledgeBase,
                @Nullable KnowledgeBaseManager kbManager,
                @Nullable ExecutionEventBus eventBus) {
            this.repository = repository;
            this.snapshotManager = snapshotManager;
            this.checkpointManager = checkpointManager;
            this.llmClient = llmClient;
            this.embeddingClient = embeddingClient;
            this.knowledgeBase = knowledgeBase;
            this.kbManager = kbManager;
            this.eventBus = eventBus;
        }
        
        @Override
        public WorkflowEngine get() {
            log.info("Initializing WorkflowEngine");
            WorkflowEngine engine = new WorkflowEngine();
            if (repository != null) engine.setRepository(repository);
            if (snapshotManager != null) engine.setSnapshotManager(snapshotManager);
            if (checkpointManager != null) engine.setCheckpointManager(checkpointManager);
            if (llmClient != null) engine.setLlmClient(llmClient);
            if (embeddingClient != null) engine.setEmbeddingClient(embeddingClient);
            if (knowledgeBase != null) engine.setKnowledgeBase(knowledgeBase);
            if (kbManager != null) engine.setKnowledgeBaseManager(kbManager);
            if (eventBus != null) engine.setEventBus(eventBus);
            return engine;
        }
    }
    
    @Singleton
    static class WorkflowApiProvider implements Provider<WorkflowApi> {
        private final WorkflowEngine engine;
        private final ServerConfig serverConfig;
        private final SnapshotManager snapshotManager;
        private final ExecutionEventBus eventBus;
        private final KnowledgeBaseManager kbManager;
        private final KnowledgeBaseRepository kbRepository;
        
        @com.google.inject.Inject
        public WorkflowApiProvider(
                WorkflowEngine engine,
                ServerConfig serverConfig,
                @Nullable SnapshotManager snapshotManager,
                @Nullable ExecutionEventBus eventBus,
                @Nullable KnowledgeBaseManager kbManager,
                @Nullable KnowledgeBaseRepository kbRepository) {
            this.engine = engine;
            this.serverConfig = serverConfig;
            this.snapshotManager = snapshotManager;
            this.eventBus = eventBus;
            this.kbManager = kbManager;
            this.kbRepository = kbRepository;
        }
        
        @Override
        public WorkflowApi get() {
            log.info("Initializing Workflow API on port {}", serverConfig.getPort());
            WorkflowApi api = new WorkflowApi(engine, serverConfig.getPort());
            if (snapshotManager != null) api.setSnapshotManager(snapshotManager);
            if (eventBus != null) api.setEventBus(eventBus);
            if (kbManager != null) {
                api.setKnowledgeApi(new KnowledgeApi(kbManager));
            } else if (kbRepository != null) {
                api.setKnowledgeApi(new KnowledgeApi(kbRepository));
            }
            return api;
        }
    }
}
