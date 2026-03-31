package com.langgraph4j.engine.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.langgraph4j.engine.config.DatabaseConfig;
import com.langgraph4j.engine.config.EmbeddingConfig;
import com.langgraph4j.engine.config.LLMConfig;
import com.langgraph4j.engine.model.EmbeddingClient;
import com.langgraph4j.engine.model.LLMClient;
import com.langgraph4j.engine.rag.PGVectorKnowledgeBase;
import com.langgraph4j.engine.rag.KnowledgeBase;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LLMModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(LLMClient.class).toProvider(LLMClientProvider.class).in(Singleton.class);
        bind(EmbeddingClient.class).toProvider(EmbeddingClientProvider.class).in(Singleton.class);
        bind(KnowledgeBase.class).toProvider(KnowledgeBaseProvider.class).in(Singleton.class);
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
            if (!pgvectorConfig.isValid()) {
                log.info("PGVector not configured, knowledge base features disabled");
                return null;
            }
            
            if (embeddingClient == null) {
                log.info("Embedding client not available, knowledge base features disabled");
                return null;
            }
            
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
                throw new ProvisionException("Failed to create KnowledgeBase: " + e.getMessage(), e);
            }
        }
    }
}
