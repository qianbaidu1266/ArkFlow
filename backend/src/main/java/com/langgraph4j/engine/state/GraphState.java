package com.langgraph4j.engine.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图状态 - 工作流执行过程中的状态容器
 * 支持嵌套结构和并发安全
 */
@Slf4j
public class GraphState {
    
    private final Map<String, Object> data;
    private final ObjectMapper objectMapper;
    
    public GraphState() {
        this.data = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
    }
    
    public GraphState(Map<String, Object> initialData) {
        this.data = new ConcurrentHashMap<>(initialData);
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 设置值
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }
    
    /**
     * 获取值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }
    
    /**
     * 获取值（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    /**
     * 获取JsonNode
     */
    public JsonNode getAsJson(String key) {
        Object value = data.get(key);
        if (value == null) return null;
        return objectMapper.valueToTree(value);
    }
    
    /**
     * 获取字符串
     */
    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 获取整数
     */
    public Integer getInt(String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 获取长整数
     */
    public Long getLong(String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 获取双精度浮点数
     */
    public Double getDouble(String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 获取布尔值
     */
    public Boolean getBoolean(String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
    
    /**
     * 获取列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof List) {
            return (List<T>) value;
        }
        return null;
    }
    
    /**
     * 获取Map
     */
    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getMap(String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Map) {
            return (Map<K, V>) value;
        }
        return null;
    }
    
    /**
     * 检查是否包含key
     */
    public boolean contains(String key) {
        return data.containsKey(key);
    }
    
    /**
     * 删除key
     */
    public void remove(String key) {
        data.remove(key);
    }
    
    /**
     * 合并另一个状态
     */
    public void merge(GraphState other) {
        if (other != null) {
            this.data.putAll(other.data);
        }
    }
    
    /**
     * 合并Map
     */
    public void merge(Map<String, Object> other) {
        if (other != null) {
            this.data.putAll(other);
        }
    }
    
    /**
     * 获取所有数据
     */
    public Map<String, Object> getAll() {
        return new HashMap<>(data);
    }
    
    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize state to JSON", e);
            return "{}";
        }
    }
    
    /**
     * 从JSON字符串创建
     */
    public static GraphState fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(json, Map.class);
            return new GraphState(map);
        } catch (Exception e) {
            log.error("Failed to deserialize state from JSON", e);
            return new GraphState();
        }
    }
    
    /**
     * 转换为ObjectNode
     */
    public ObjectNode toObjectNode() {
        return objectMapper.valueToTree(data);
    }
    
    /**
     * 创建副本
     */
    public GraphState copy() {
        return new GraphState(new HashMap<>(this.data));
    }
    
    /**
     * 清空状态
     */
    public void clear() {
        data.clear();
    }
    
    /**
     * 获取key集合
     */
    public Set<String> keys() {
        return new HashSet<>(data.keySet());
    }
    
    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }
    
    /**
     * 获取大小
     */
    public int size() {
        return data.size();
    }
    
    @Override
    public String toString() {
        return "GraphState{" + data + '}';
    }
}
