package com.langgraph4j.engine.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 边 - 连接节点的有向边
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Edge {
    private String from;
    private String to;
    private EdgeType type;
    private Condition condition;
    private Map<String, Object> metadata;
    
    public Edge(String from, String to, EdgeType type, Condition condition) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.condition = condition;
    }
}
