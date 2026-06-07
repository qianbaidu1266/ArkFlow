package com.langgraph4j.engine.di;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.langgraph4j.engine.config.DatabaseConfig;
import com.langgraph4j.engine.config.EmbeddingConfig;
import com.langgraph4j.engine.config.EngineConfig;
import com.langgraph4j.engine.config.LLMConfig;
import com.langgraph4j.engine.config.MilvusConfig;
import com.langgraph4j.engine.config.RedisConfig;
import com.langgraph4j.engine.config.RerankerConfig;
import com.langgraph4j.engine.config.ServerConfig;

public class ConfigModule extends AbstractModule {
    
    private final EngineConfig config;
    
    public ConfigModule(EngineConfig config) {
        this.config = config;
    }
    
    @Override
    protected void configure() {
        bind(EngineConfig.class).toInstance(config);
        bind(ServerConfig.class).toInstance(config.getServer());
        bind(DatabaseConfig.class).annotatedWith(Names.named("mysql")).toInstance(config.getMysql());
        bind(DatabaseConfig.class).annotatedWith(Names.named("pgvector")).toInstance(config.getPgvector());
        bind(LLMConfig.class).toInstance(config.getLlm());
        bind(EmbeddingConfig.class).toInstance(config.getEmbedding());
        bind(RedisConfig.class).toInstance(config.getRedis());
        bind(MilvusConfig.class).toInstance(config.getMilvus());
        bind(RerankerConfig.class).toInstance(config.getReranker());
    }
}
