package com.langgraph4j.engine.state;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 检查点管理器实现
 * 支持分布式状态管理
 */
@Slf4j
public class RedisCheckpointManager implements CheckpointManager {
    
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final String keyPrefix;
    private final Map<String, AtomicLong> sequenceCounters;
    
    public RedisCheckpointManager(String redisUri, String keyPrefix) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.commands = connection.sync();
        this.keyPrefix = keyPrefix != null ? keyPrefix : "langgraph4j:checkpoint:";
        this.sequenceCounters = new ConcurrentHashMap<>();
    }
    
    public RedisCheckpointManager(String redisUri) {
        this(redisUri, null);
    }
    
    @Override
    public void saveCheckpoint(String executionId, String nodeId, GraphState state) {
        try {
            String key = buildKey(executionId, nodeId);
            long sequence = getNextSequence(executionId);
            long timestamp = System.currentTimeMillis();
            
            Checkpoint checkpoint = new Checkpoint(executionId, nodeId, state, timestamp, sequence);
            String json = state.toJson();
            
            // 保存检查点数据
            commands.hset(key, "data", json);
            commands.hset(key, "timestamp", String.valueOf(timestamp));
            commands.hset(key, "sequence", String.valueOf(sequence));
            
            // 添加到执行的检查点列表
            commands.zadd(buildExecutionKey(executionId), sequence, nodeId);
            
            log.debug("Checkpoint saved: execution={}, node={}, sequence={}", executionId, nodeId, sequence);
        } catch (Exception e) {
            log.error("Failed to save checkpoint", e);
            throw new RuntimeException("Failed to save checkpoint", e);
        }
    }
    
    @Override
    public GraphState restoreCheckpoint(String executionId, String nodeId) {
        try {
            String key = buildKey(executionId, nodeId);
            Map<String, String> data = commands.hgetall(key);
            
            if (data == null || data.isEmpty()) {
                log.warn("Checkpoint not found: execution={}, node={}", executionId, nodeId);
                return null;
            }
            
            String json = data.get("data");
            return GraphState.fromJson(json);
        } catch (Exception e) {
            log.error("Failed to restore checkpoint", e);
            return null;
        }
    }
    
    @Override
    public List<Checkpoint> getCheckpoints(String executionId) {
        List<Checkpoint> checkpoints = new ArrayList<>();
        
        try {
            // 获取所有节点ID
            List<String> nodeIds = commands.zrange(buildExecutionKey(executionId), 0, -1);
            
            for (String nodeId : nodeIds) {
                String key = buildKey(executionId, nodeId);
                Map<String, String> data = commands.hgetall(key);
                
                if (data != null && !data.isEmpty()) {
                    Checkpoint checkpoint = new Checkpoint();
                    checkpoint.setExecutionId(executionId);
                    checkpoint.setNodeId(nodeId);
                    checkpoint.setState(GraphState.fromJson(data.get("data")));
                    checkpoint.setTimestamp(Long.parseLong(data.getOrDefault("timestamp", "0")));
                    checkpoint.setSequence(Long.parseLong(data.getOrDefault("sequence", "0")));
                    checkpoints.add(checkpoint);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get checkpoints", e);
        }
        
        return checkpoints;
    }
    
    @Override
    public Checkpoint getLatestCheckpoint(String executionId) {
        try {
            // 获取最新的节点ID
            List<String> nodeIds = commands.zrevrange(buildExecutionKey(executionId), 0, 0);
            
            if (nodeIds.isEmpty()) {
                return null;
            }
            
            String nodeId = nodeIds.get(0);
            String key = buildKey(executionId, nodeId);
            Map<String, String> data = commands.hgetall(key);
            
            if (data != null && !data.isEmpty()) {
                Checkpoint checkpoint = new Checkpoint();
                checkpoint.setExecutionId(executionId);
                checkpoint.setNodeId(nodeId);
                checkpoint.setState(GraphState.fromJson(data.get("data")));
                checkpoint.setTimestamp(Long.parseLong(data.getOrDefault("timestamp", "0")));
                checkpoint.setSequence(Long.parseLong(data.getOrDefault("sequence", "0")));
                return checkpoint;
            }
        } catch (Exception e) {
            log.error("Failed to get latest checkpoint", e);
        }
        
        return null;
    }
    
    @Override
    public void deleteCheckpoint(String executionId, String nodeId) {
        try {
            String key = buildKey(executionId, nodeId);
            commands.del(key);
            commands.zrem(buildExecutionKey(executionId), nodeId);
            log.debug("Checkpoint deleted: execution={}, node={}", executionId, nodeId);
        } catch (Exception e) {
            log.error("Failed to delete checkpoint", e);
        }
    }
    
    @Override
    public void deleteExecutionCheckpoints(String executionId) {
        try {
            // 获取所有节点ID
            List<String> nodeIds = commands.zrange(buildExecutionKey(executionId), 0, -1);
            
            // 删除每个检查点
            for (String nodeId : nodeIds) {
                commands.del(buildKey(executionId, nodeId));
            }
            
            // 删除执行的检查点列表
            commands.del(buildExecutionKey(executionId));
            
            // 删除序列计数器
            sequenceCounters.remove(executionId);
            
            log.debug("All checkpoints deleted for execution: {}", executionId);
        } catch (Exception e) {
            log.error("Failed to delete execution checkpoints", e);
        }
    }
    
    /**
     * 关闭连接
     */
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
    
    private String buildKey(String executionId, String nodeId) {
        return keyPrefix + executionId + ":" + nodeId;
    }
    
    private String buildExecutionKey(String executionId) {
        return keyPrefix + executionId + ":nodes";
    }
    
    private long getNextSequence(String executionId) {
        return sequenceCounters.computeIfAbsent(executionId, k -> new AtomicLong(0)).incrementAndGet();
    }
}
