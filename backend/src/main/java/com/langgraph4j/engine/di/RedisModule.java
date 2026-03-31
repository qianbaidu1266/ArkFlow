package com.langgraph4j.engine.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.langgraph4j.engine.config.RedisConfig;
import com.langgraph4j.engine.state.RedisCheckpointManager;
import com.langgraph4j.engine.state.CheckpointManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisModule extends AbstractModule {
    
    @Override
    protected void configure() {
        bind(CheckpointManager.class).toProvider(CheckpointManagerProvider.class).in(Singleton.class);
    }
    
    @Singleton
    static class CheckpointManagerProvider implements Provider<CheckpointManager> {
        
        private final RedisConfig config;
        
        @com.google.inject.Inject
        public CheckpointManagerProvider(RedisConfig config) {
            this.config = config;
        }
        
        @Override
        public CheckpointManager get() {
            if (!config.isValid()) {
                log.info("Redis not configured, checkpoint features disabled");
                return null;
            }
            
            try {
                log.info("Initializing Redis checkpoint manager: {}", config.getUri());
                return new RedisCheckpointManager(config.getUri());
            } catch (Exception e) {
                throw new ProvisionException("Failed to create CheckpointManager: " + e.getMessage(), e);
            }
        }
    }
}
