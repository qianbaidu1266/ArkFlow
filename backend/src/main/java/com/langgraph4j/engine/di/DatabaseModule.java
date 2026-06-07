package com.langgraph4j.engine.di;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.AbstractModule;
import com.google.inject.ProvisionException;
import com.google.inject.name.Named;
import com.langgraph4j.engine.config.DatabaseConfig;
import com.langgraph4j.engine.repository.KnowledgeBaseRepository;
import com.langgraph4j.engine.repository.WorkflowRepository;
import com.langgraph4j.engine.state.JdbcSnapshotManager;
import com.langgraph4j.engine.state.SnapshotManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DatabaseModule extends AbstractModule {
    
    public static final Named MYSQL = com.google.inject.name.Names.named("mysql");
    
    @Override
    protected void configure() {
        bind(WorkflowRepository.class).toProvider(WorkflowRepositoryProvider.class).in(Singleton.class);
        bind(SnapshotManager.class).toProvider(SnapshotManagerProvider.class).in(Singleton.class);
        bind(KnowledgeBaseRepository.class).toProvider(KnowledgeBaseRepositoryProvider.class).in(Singleton.class);
    }
    
    @Singleton
    static class WorkflowRepositoryProvider implements Provider<WorkflowRepository> {
        
        private final DatabaseConfig config;
        
        @com.google.inject.Inject
        public WorkflowRepositoryProvider(@Named("mysql") DatabaseConfig config) {
            this.config = config;
        }
        
        @Override
        public WorkflowRepository get() {
            if (!config.isValid()) {
                log.info("MySQL not configured, workflow persistence disabled");
                return null;
            }
            
            try {
                log.info("Initializing MySQL repository: {}", config.getUrl());
                return new WorkflowRepository(config.getUrl(), config.getUsername(), config.getPassword());
            } catch (Exception e) {
                throw new ProvisionException("Failed to create WorkflowRepository: " + e.getMessage(), e);
            }
        }
    }
    
    @Singleton
    static class SnapshotManagerProvider implements Provider<SnapshotManager> {
        
        private final DatabaseConfig config;
        
        @com.google.inject.Inject
        public SnapshotManagerProvider(@Named("mysql") DatabaseConfig config) {
            this.config = config;
        }
        
        @Override
        public SnapshotManager get() {
            if (!config.isValid()) {
                log.info("MySQL not configured, snapshot persistence disabled");
                return null;
            }
            
            try {
                log.info("Initializing JDBC snapshot manager: {}", config.getUrl());
                return new JdbcSnapshotManager(config.getUrl(), config.getUsername(), config.getPassword());
            } catch (Exception e) {
                throw new ProvisionException("Failed to create SnapshotManager: " + e.getMessage(), e);
            }
        }
    }
    
    @Singleton
    static class KnowledgeBaseRepositoryProvider implements Provider<KnowledgeBaseRepository> {
        
        private final DatabaseConfig config;
        
        @com.google.inject.Inject
        public KnowledgeBaseRepositoryProvider(@Named("mysql") DatabaseConfig config) {
            this.config = config;
        }
        
        @Override
        public KnowledgeBaseRepository get() {
            if (!config.isValid()) {
                log.info("MySQL not configured, knowledge base repository disabled");
                return null;
            }
            
            try {
                log.info("Initializing KnowledgeBase repository: {}", config.getUrl());
                return new KnowledgeBaseRepository(config.getUrl(), config.getUsername(), config.getPassword());
            } catch (Exception e) {
                throw new ProvisionException("Failed to create KnowledgeBaseRepository: " + e.getMessage(), e);
            }
        }
    }
}
