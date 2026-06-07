package com.langgraph4j.engine.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.langgraph4j.engine.config.DatabaseConfig;
import com.langgraph4j.engine.config.EmbeddingConfig;
import com.langgraph4j.engine.config.LLMConfig;
import com.langgraph4j.engine.config.MilvusConfig;
import com.langgraph4j.engine.config.RerankerConfig;
import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.model.LLMClient;
import com.langgraph4j.engine.rag.PGVectorKnowledgeBase;
import com.langgraph4j.engine.rag.KnowledgeBase;
import com.langgraph4j.engine.rag.KnowledgeBaseManager;
import com.langgraph4j.engine.rag.RerankerClient;
import com.langgraph4j.engine.repository.KnowledgeBaseRepository;
import com.google.inject.name.Named;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LLMModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(LLMClient.class).toProvider(LLMClientProvider.class).in(Singleton.class);
        bind(EmbeddingClient.class).toProvider(EmbeddingClientProvider.class).in(Singleton.class);
        bind(KnowledgeBase.class).toProvider(KnowledgeBaseProvider.class).in(Singleton.class);
        bind(KnowledgeBaseManager.class).toProvider(KnowledgeBaseManagerProvider.class).in(Singleton.class);
    }
    
    @Singleton
    static class LLMClientProvider implements Provider<LLMClient> {
        
        private final LLMConfig config;
        
        @com.google.inject.Inject
        public LLMClientProvider(LLMConfig config) {
            this.config = config;
        }
        
        @Override
        public LLMClient get() {
            if (!config.isValid()) {
                log.info("LLM not configured, LLM features disabled");
                return null;
            }
            
            try {
                log.info("Initializing LLM client: {} at {}", config.getModel(), config.getBaseUrl());
                return new LLMClient(config.getBaseUrl(), config.getApiKey(), config.getModel(), null);
            } catch (Exception e) {
                throw new ProvisionException("Failed to create LLMClient: " + e.getMessage(), e);
            }
        }
    }
    
    @Singleton
    static class EmbeddingClientProvider implements Provider<EmbeddingClient> {
        
        private final EmbeddingConfig config;
        
        @com.google.inject.Inject
        public EmbeddingClientProvider(EmbeddingConfig config) {
            this.config = config;
        }
        
        @Override
        public EmbeddingClient get() {
            if (!config.isValid()) {
                log.info("Embedding not configured, embedding features disabled");
                return null;
            }
            
            try {
                log.info("Initializing Embedding client: {} at {}", config.getModel(), config.getBaseUrl());
                return new EmbeddingClient(
                    config.getBaseUrl(), 
                    config.getApiKey(), 
                    config.getModel(), 
                    config.getDimensions(), 
                    null
                );
            } catch (Exception e) {
                throw new ProvisionException("Failed to create EmbeddingClient: " + e.getMessage(), e);
            }
        }
    }
    
    @Singleton
    static class KnowledgeBaseProvider implements Provider<KnowledgeBase> {

        private final DatabaseConfig pgvectorConfig;
        private final EmbeddingConfig embeddingConfig;
        private final EmbeddingClient embeddingClient;

        @com.google.inject.Inject
        public KnowledgeBaseProvider(
                @Named("pgvector") DatabaseConfig pgvectorConfig,
                EmbeddingConfig embeddingConfig,
                EmbeddingClient embeddingClient) {
            this.pgvectorConfig = pgvectorConfig;
            this.embeddingConfig = embeddingConfig;
            this.embeddingClient = embeddingClient;
        }
        
        @Override
        public KnowledgeBase get() {
            if (embeddingClient == null) {
                log.info("Embedding client not available, knowledge base features disabled");
                return null;
            }

            // Milvus 模式下由 KnowledgeBaseManager 统一管理，此处不再创建独立连接
            // 回退到 PGVector
            if (pgvectorConfig.isValid()) {
                try {
                    log.info("Initializing PGVector knowledge base: {}", pgvectorConfig.getUrl());
                    return new PGVectorKnowledgeBase(
                        "default",
                        "Default Knowledge Base",
                        pgvectorConfig.getUrl(),
                        pgvectorConfig.getUsername(),
                        pgvectorConfig.getPassword(),
                        embeddingClient,
                        null,
                        embeddingConfig.getDimensions()
                    );
                } catch (Exception e) {
                    throw new ProvisionException("Failed to create PGVector KnowledgeBase: " + e.getMessage(), e);
                }
            }

            log.info("No vector database configured (Milvus handled by KnowledgeBaseManager, PGVector disabled)");
            return null;
        }
    }
    
    @Singleton
    static class KnowledgeBaseManagerProvider implements Provider<KnowledgeBaseManager> {
        
        private final MilvusConfig milvusConfig;
        private final RerankerConfig rerankerConfig;
        private final EmbeddingConfig embeddingConfig;
        private final EmbeddingClient embeddingClient;
        private final KnowledgeBaseRepository repository;
        
        @com.google.inject.Inject
        public KnowledgeBaseManagerProvider(
                MilvusConfig milvusConfig,
                RerankerConfig rerankerConfig,
                EmbeddingConfig embeddingConfig,
                EmbeddingClient embeddingClient,
                @Nullable KnowledgeBaseRepository repository) {
            this.milvusConfig = milvusConfig;
            this.rerankerConfig = rerankerConfig;
            this.embeddingConfig = embeddingConfig;
            this.embeddingClient = embeddingClient;
            this.repository = repository;
        }
        
        @Override
        public KnowledgeBaseManager get() {
            if (embeddingClient == null) {
                log.info("Embedding client not available, knowledge base manager disabled");
                return null;
            }
            
            if (!milvusConfig.isValid()) {
                log.info("Milvus not configured, knowledge base manager (CRUD-only) initialized");
                if (repository == null) {
                    return null;
                }
                return new KnowledgeBaseManager(null, 0, null, null, embeddingClient, repository, null, embeddingConfig.getDimensions(), false, false);
            }
            
            try {
                RerankerClient rerankerClient = null;
                if (milvusConfig.isEnableReranker() && rerankerConfig.isValid()) {
                    rerankerClient = new RerankerClient(
                            rerankerConfig.getBaseUrl(),
                            rerankerConfig.getApiKey(),
                            rerankerConfig.getModel());
                }

                log.info("Initializing KnowledgeBaseManager: {}:{}", milvusConfig.getHost(), milvusConfig.getPort());
                return new KnowledgeBaseManager(
                    milvusConfig.getHost(),
                    milvusConfig.getPort(),
                    milvusConfig.getToken(),
                    milvusConfig.getDbName(),
                    embeddingClient,
                    repository,
                    rerankerClient,
                    embeddingConfig.getDimensions(),
                    milvusConfig.isEnableBM25(),
                    milvusConfig.isEnableReranker()
                );
            } catch (Exception e) {
                log.warn("Milvus connection failed ({}), falling back to CRUD-only mode: {}", e.getClass().getSimpleName(), e.getMessage());
                // 回退到 CRUD-only 模式（仅 MySQL，无向量检索）
                if (repository == null) return null;
                return new KnowledgeBaseManager(null, 0, null, null, embeddingClient, repository, null, embeddingConfig.getDimensions(), false, false);
            }
        }
    }
}
