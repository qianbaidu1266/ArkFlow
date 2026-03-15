package com.langgraph4j.engine.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 核心图结构 - 工作流的图表示
 * 支持有向图、并行执行、条件分支
 */
@Slf4j
public class Graph {
    
    private final String id;
    private final String name;
    private final Map<String, Node> nodes;
    private final Map<String, List<Edge>> edges;
    private final Map<String, List<Edge>> conditionalEdges;
    private String entryPoint;
    private final Map<String, Object> metadata;
    
    public Graph(String id, String name) {
        this.id = id;
        this.name = name;
        this.nodes = new ConcurrentHashMap<>();
        this.edges = new ConcurrentHashMap<>();
        this.conditionalEdges = new ConcurrentHashMap<>();
        this.metadata = new ConcurrentHashMap<>();
    }
    
    /**
     * 添加节点
     */
    public Graph addNode(Node node) {
        nodes.put(node.getId(), node);
        log.debug("Added node: {} ({})", node.getId(), node.getType());
        return this;
    }
    
    /**
     * 添加普通边
     */
    public Graph addEdge(String from, String to) {
        edges.computeIfAbsent(from, k -> new ArrayList<>())
             .add(new Edge(from, to, EdgeType.NORMAL, null));
        log.debug("Added edge: {} -> {}", from, to);
        return this;
    }
    
    /**
     * 添加条件边
     */
    public Graph addConditionalEdge(String from, Map<String, String> conditions, String defaultTarget) {
        edges.computeIfAbsent(from, k -> new ArrayList<>())
             .add(new Edge(from, null, EdgeType.CONDITIONAL, new Condition(conditions, defaultTarget)));
        log.debug("Added conditional edge from: {}", from);
        return this;
    }
    
    /**
     * 设置入口点
     */
    public Graph setEntryPoint(String nodeId) {
        this.entryPoint = nodeId;
        log.debug("Set entry point: {}", nodeId);
        return this;
    }
    
    /**
     * 执行图
     */
    public CompletableFuture<GraphState> execute(GraphState initialState, ExecutionContext context) {
        return executeNode(entryPoint, initialState, context, new HashSet<>());
    }
    
    /**
     * 递归执行节点
     */
    private CompletableFuture<GraphState> executeNode(String nodeId, GraphState state, 
                                                       ExecutionContext context, Set<String> visited) {
        if (visited.contains(nodeId)) {
            log.warn("Detected cycle at node: {}, stopping execution", nodeId);
            return CompletableFuture.completedFuture(state);
        }
        
        Node node = nodes.get(nodeId);
        if (node == null) {
            log.error("Node not found: {}", nodeId);
            return CompletableFuture.completedFuture(state);
        }
        
        // 记录检查点
        context.saveCheckpoint(nodeId, state);
        
        visited.add(nodeId);
        
        // 执行当前节点
        return node.execute(state, context)
            .thenCompose(resultState -> {
                // 获取下一个节点
                List<String> nextNodes = getNextNodes(nodeId, resultState);
                
                if (nextNodes.isEmpty()) {
                    // 没有下一个节点，执行结束
                    return CompletableFuture.completedFuture(resultState);
                }
                
                // 并行执行所有分支
                List<CompletableFuture<GraphState>> futures = nextNodes.stream()
                    .map(nextId -> executeNode(nextId, resultState, context, new HashSet<>(visited)))
                    .collect(Collectors.toList());
                
                // 合并所有分支的结果
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> mergeStates(futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())));
            });
    }
    
    /**
     * 获取下一个节点列表（支持并行分支）
     */
    private List<String> getNextNodes(String from, GraphState state) {
        List<Edge> outgoingEdges = edges.get(from);
        if (outgoingEdges == null || outgoingEdges.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> nextNodes = new ArrayList<>();
        
        for (Edge edge : outgoingEdges) {
            if (edge.getType() == EdgeType.NORMAL) {
                nextNodes.add(edge.getTo());
            } else if (edge.getType() == EdgeType.CONDITIONAL) {
                String target = evaluateCondition(edge.getCondition(), state);
                if (target != null) {
                    nextNodes.add(target);
                }
            }
        }
        
        return nextNodes;
    }
    
    /**
     * 评估条件
     */
    private String evaluateCondition(Condition condition, GraphState state) {
        if (condition == null) return null;
        
        JsonNode conditionValue = state.get("__condition_result");
        if (conditionValue != null) {
            String value = conditionValue.asText();
            String target = condition.getConditions().get(value);
            if (target != null) {
                return target;
            }
        }
        
        return condition.getDefaultTarget();
    }
    
    /**
     * 合并多个状态
     */
    private GraphState mergeStates(List<GraphState> states) {
        if (states.isEmpty()) {
            return new GraphState();
        }
        if (states.size() == 1) {
            return states.get(0);
        }
        
        GraphState merged = new GraphState();
        for (GraphState state : states) {
            merged.merge(state);
        }
        return merged;
    }
    
    /**
     * 验证图的完整性
     */
    public boolean validate() {
        if (entryPoint == null || !nodes.containsKey(entryPoint)) {
            log.error("Invalid entry point: {}", entryPoint);
            return false;
        }
        
        // 检查所有边的目标节点是否存在
        for (Map.Entry<String, List<Edge>> entry : edges.entrySet()) {
            for (Edge edge : entry.getValue()) {
                if (edge.getType() == EdgeType.NORMAL) {
                    if (!nodes.containsKey(edge.getTo())) {
                        log.error("Edge target not found: {}", edge.getTo());
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * 获取拓扑排序
     */
    public List<String> getTopologicalOrder() {
        Map<String, Integer> inDegree = new HashMap<>();
        nodes.keySet().forEach(k -> inDegree.put(k, 0));
        
        for (List<Edge> edgeList : edges.values()) {
            for (Edge edge : edgeList) {
                if (edge.getType() == EdgeType.NORMAL && edge.getTo() != null) {
                    inDegree.merge(edge.getTo(), 1, Integer::sum);
                }
            }
        }
        
        Queue<String> queue = new LinkedList<>();
        inDegree.forEach((k, v) -> {
            if (v == 0) queue.offer(k);
        });
        
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);
            
            List<Edge> outgoing = edges.get(node);
            if (outgoing != null) {
                for (Edge edge : outgoing) {
                    if (edge.getType() == EdgeType.NORMAL && edge.getTo() != null) {
                        int newDegree = inDegree.get(edge.getTo()) - 1;
                        inDegree.put(edge.getTo(), newDegree);
                        if (newDegree == 0) {
                            queue.offer(edge.getTo());
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Node> getNodes() { return new HashMap<>(nodes); }
    public Map<String, List<Edge>> getEdges() { return new HashMap<>(edges); }
    public String getEntryPoint() { return entryPoint; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
}
