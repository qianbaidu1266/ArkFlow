package com.langgraph4j.engine.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.langgraph4j.engine.core.*;
import com.langgraph4j.engine.node.NodeFactory;
import com.langgraph4j.engine.state.GraphState;
import com.langgraph4j.engine.state.NodeExecutionSnapshot;
import com.langgraph4j.engine.state.SnapshotManager;
import com.langgraph4j.engine.websocket.ExecutionEventBus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class WorkflowApi extends AbstractVerticle {
    
    private final WorkflowEngine engine;
    private final ObjectMapper objectMapper;
    private final int port;
    private SnapshotManager snapshotManager;
    private ExecutionEventBus eventBus;
    
    public WorkflowApi(WorkflowEngine engine, int port) {
        this.engine = engine;
        this.objectMapper = new ObjectMapper();
        this.port = port;
    }
    
    public void setSnapshotManager(SnapshotManager snapshotManager) {
        this.snapshotManager = snapshotManager;
    }
    
    public void setEventBus(ExecutionEventBus eventBus) {
        this.eventBus = eventBus;
    }
    
    @Override
    public void start(Promise<Void> startPromise) {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        
        // CORS
        router.route().handler(CorsHandler.create()
            .addOrigin("*")
            .allowedMethod(io.vertx.core.http.HttpMethod.GET)
            .allowedMethod(io.vertx.core.http.HttpMethod.POST)
            .allowedMethod(io.vertx.core.http.HttpMethod.PUT)
            .allowedMethod(io.vertx.core.http.HttpMethod.DELETE)
            .allowedMethod(io.vertx.core.http.HttpMethod.OPTIONS)
            .allowedHeader("Content-Type")
            .allowedHeader("Authorization")
        );
        
        // Body handler
        router.route().handler(BodyHandler.create());
        
        // Routes
        router.get("/api/workflows").handler(this::listWorkflows);
        router.get("/api/workflows/:id").handler(this::getWorkflow);
        router.post("/api/workflows").handler(this::createWorkflow);
        router.put("/api/workflows/:id").handler(this::updateWorkflow);
        router.delete("/api/workflows/:id").handler(this::deleteWorkflow);
        
        router.post("/api/workflows/:id/execute").handler(this::executeWorkflow);
        router.get("/api/executions").handler(this::listExecutions);
        router.get("/api/executions/:id").handler(this::getExecution);
        router.get("/api/executions/:id/checkpoints").handler(this::getCheckpoints);
        router.get("/api/executions/:id/snapshots").handler(this::getExecutionSnapshots);
        router.get("/api/executions/:id/snapshots/:nodeId").handler(this::getNodeSnapshot);
        
        router.get("/api/node-types").handler(this::listNodeTypes);
        
        server.requestHandler(router);
        
        server.webSocketHandler(ws -> {
            String path = ws.path();
            
            if (path.startsWith("/ws/execution/")) {
                String executionId = path.substring("/ws/execution/".length());
                
                if (eventBus != null) {
                    eventBus.addConnection(executionId, ws);
                    
                    ws.closeHandler(v -> {
                        eventBus.removeConnection(executionId);
                    });
                    
                    ws.exceptionHandler(e -> {
                        log.error("WebSocket error for execution: {}", executionId, e);
                        eventBus.removeConnection(executionId);
                    });
                }
            } else {
                ws.reject();
            }
        });
        
        server.listen(port, result -> {
            if (result.succeeded()) {
                log.info("Workflow API server started on port {}", port);
                startPromise.complete();
            } else {
                log.error("Failed to start API server", result.cause());
                startPromise.fail(result.cause());
            }
        });
    }
    
    /**
     * 列出所有工作流
     */
    private void listWorkflows(RoutingContext ctx) {
        try {
            List<Map<String, Object>> workflowList = engine.listWorkflows(0, 100);
            
            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode workflows = response.putArray("workflows");
            
            for (Map<String, Object> wf : workflowList) {
                ObjectNode wfNode = workflows.addObject();
                wfNode.put("id", (String) wf.get("id"));
                wfNode.put("name", (String) wf.get("name"));
                wfNode.put("status", (String) wf.get("status"));
                wfNode.put("createdAt", (Long) wf.get("createdAt"));
                wfNode.put("updatedAt", (Long) wf.get("updatedAt"));
                wfNode.put("nodeCount", (Integer) wf.get("nodeCount"));
                wfNode.put("edgeCount", (Integer) wf.get("edgeCount"));
            }
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.toString());
        } catch (Exception e) {
            log.error("Failed to list workflows", e);
            ctx.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    /**
     * 获取工作流详情
     */
    private void getWorkflow(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        Graph workflow = engine.getWorkflow(id);
        
        if (workflow == null) {
            ctx.response()
                .setStatusCode(404)
                .end(new JsonObject().put("error", "Workflow not found").encode());
            return;
        }
        
        ObjectNode response = serializeWorkflow(workflow);
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.toString());
    }
    
    /**
     * 创建工作流
     */
    private void createWorkflow(RoutingContext ctx) {
        try {
            JsonObject body = ctx.body().asJsonObject();
            
            String id = body.getString("id", UUID.randomUUID().toString());
            String name = body.getString("name", "Untitled Workflow");
            
            Graph workflow = parseWorkflow(body, id);
            engine.registerWorkflow(id, workflow);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("id", id);
            response.put("name", name);
            response.put("status", "created");
            
            ctx.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(response.toString());
                
        } catch (Exception e) {
            log.error("Failed to create workflow", e);
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    /**
     * 更新工作流
     */
    private void updateWorkflow(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonObject body = ctx.body().asJsonObject();
            
            Graph workflow = parseWorkflow(body, id);
            engine.registerWorkflow(id, workflow);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("id", id);
            response.put("status", "updated");
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.toString());
                
        } catch (Exception e) {
            log.error("Failed to update workflow", e);
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    /**
     * 删除工作流
     */
    private void deleteWorkflow(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        engine.removeWorkflow(id);
        
        ctx.response()
            .setStatusCode(204)
            .end();
    }
    
    /**
     * 执行工作流
     */
    private void executeWorkflow(RoutingContext ctx) {
        try {
            String id = ctx.pathParam("id");
            JsonObject body = ctx.body().asJsonObject();
            
            Graph workflow = engine.getWorkflow(id);
            if (workflow == null) {
                ctx.response()
                    .setStatusCode(404)
                    .end(new JsonObject().put("error", "Workflow not found").encode());
                return;
            }
            
            // 构建输入状态
            GraphState input = new GraphState();
            if (body != null && body.containsKey("inputs")) {
                JsonObject inputs = body.getJsonObject("inputs");
                inputs.stream().forEach(entry -> {
                    input.set(entry.getKey(), entry.getValue());
                });
            }
            
            // 获取可选的 executionId
            String executionId = null;
            if (body != null && body.containsKey("executionId")) {
                executionId = body.getString("executionId");
            }
            
            // 执行配置
            WorkflowEngine.ExecutionConfig config = null;
            if (body != null && body.containsKey("config")) {
                JsonObject configJson = body.getJsonObject("config");
                config = WorkflowEngine.ExecutionConfig.builder()
                    .timeout(configJson.getInteger("timeout", 30000))
                    .enableCheckpoint(configJson.getBoolean("enableCheckpoint", true))
                    .executionId(executionId)
                    .build();
            } else if (executionId != null) {
                config = WorkflowEngine.ExecutionConfig.builder()
                    .executionId(executionId)
                    .build();
            }
            
            // 执行工作流
            engine.execute(id, input, config)
                .thenAccept(result -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("executionId", result.getExecutionId());
                    response.put("success", result.isSuccess());
                    response.put("duration", result.getDuration());
                    
                    if (result.isSuccess()) {
                        response.set("output", objectMapper.valueToTree(result.getOutput().getAll()));
                    } else {
                        response.put("error", result.getError());
                    }
                    
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(response.toString());
                })
                .exceptionally(e -> {
                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("error", e.getMessage()).encode());
                    return null;
                });
                
        } catch (Exception e) {
            log.error("Failed to execute workflow", e);
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    /**
     * 获取执行结果
     */
    private void getExecution(RoutingContext ctx) {
        String id = ctx.pathParam("id");
        WorkflowEngine.ExecutionResult result = engine.getExecutionResult(id);
        
        if (result == null) {
            ctx.response()
                .setStatusCode(404)
                .end(new JsonObject().put("error", "Execution not found").encode());
            return;
        }
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("executionId", result.getExecutionId());
        response.put("workflowId", result.getWorkflowId());
        response.put("success", result.isSuccess());
        response.put("duration", result.getDuration());
        
        if (result.isSuccess()) {
            response.set("output", objectMapper.valueToTree(result.getOutput().getAll()));
        } else {
            response.put("error", result.getError());
        }
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.toString());
    }
    
    /**
     * 获取检查点
     */
    private void getCheckpoints(RoutingContext ctx) {
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("checkpoints");
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.toString());
    }
    
    private void getExecutionSnapshots(RoutingContext ctx) {
        String executionId = ctx.pathParam("id");
        
        if (snapshotManager == null) {
            ctx.response()
                .setStatusCode(503)
                .end(new JsonObject().put("error", "Snapshot manager not configured").encode());
            return;
        }
        
        try {
            List<NodeExecutionSnapshot> snapshots = snapshotManager.getSnapshots(executionId);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("executionId", executionId);
            
            ArrayNode snapshotsArray = response.putArray("snapshots");
            for (NodeExecutionSnapshot snapshot : snapshots) {
                ObjectNode snapshotNode = snapshotsArray.addObject();
                snapshotNode.put("id", snapshot.getId());
                snapshotNode.put("nodeId", snapshot.getNodeId());
                snapshotNode.put("nodeType", snapshot.getNodeType());
                snapshotNode.put("nodeName", snapshot.getNodeName());
                snapshotNode.put("status", snapshot.getStatus().name());
                snapshotNode.put("startTime", snapshot.getStartTime());
                snapshotNode.put("endTime", snapshot.getEndTime());
                snapshotNode.put("duration", snapshot.getDuration());
                
                if (snapshot.getInputs() != null) {
                    snapshotNode.set("inputs", objectMapper.valueToTree(snapshot.getInputs()));
                }
                if (snapshot.getOutputs() != null) {
                    snapshotNode.set("outputs", objectMapper.valueToTree(snapshot.getOutputs()));
                }
                if (snapshot.getErrorMessage() != null) {
                    snapshotNode.put("errorMessage", snapshot.getErrorMessage());
                }
                if (snapshot.getMetadata() != null) {
                    snapshotNode.set("metadata", objectMapper.valueToTree(snapshot.getMetadata()));
                }
                
                snapshotNode.put("promptTokens", snapshot.getPromptTokens() != null ? snapshot.getPromptTokens() : 0);
                snapshotNode.put("completionTokens", snapshot.getCompletionTokens() != null ? snapshot.getCompletionTokens() : 0);
                snapshotNode.put("totalTokens", snapshot.getTotalTokens() != null ? snapshot.getTotalTokens() : 0);
            }
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.toString());
                
        } catch (Exception e) {
            log.error("Failed to get execution snapshots", e);
            ctx.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    private void getNodeSnapshot(RoutingContext ctx) {
        String executionId = ctx.pathParam("id");
        String nodeId = ctx.pathParam("nodeId");
        
        if (snapshotManager == null) {
            ctx.response()
                .setStatusCode(503)
                .end(new JsonObject().put("error", "Snapshot manager not configured").encode());
            return;
        }
        
        try {
            NodeExecutionSnapshot snapshot = snapshotManager.getSnapshot(executionId, nodeId);
            
            if (snapshot == null) {
                ctx.response()
                    .setStatusCode(404)
                    .end(new JsonObject().put("error", "Snapshot not found").encode());
                return;
            }
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("id", snapshot.getId());
            response.put("executionId", snapshot.getExecutionId());
            response.put("nodeId", snapshot.getNodeId());
            response.put("nodeType", snapshot.getNodeType());
            response.put("nodeName", snapshot.getNodeName());
            response.put("status", snapshot.getStatus().name());
            response.put("startTime", snapshot.getStartTime());
            response.put("endTime", snapshot.getEndTime());
            response.put("duration", snapshot.getDuration());
            
            if (snapshot.getInputs() != null) {
                response.set("inputs", objectMapper.valueToTree(snapshot.getInputs()));
            }
            if (snapshot.getOutputs() != null) {
                response.set("outputs", objectMapper.valueToTree(snapshot.getOutputs()));
            }
            if (snapshot.getErrorMessage() != null) {
                response.put("errorMessage", snapshot.getErrorMessage());
            }
            if (snapshot.getErrorStack() != null) {
                response.put("errorStack", snapshot.getErrorStack());
            }
            if (snapshot.getMetadata() != null) {
                response.set("metadata", objectMapper.valueToTree(snapshot.getMetadata()));
            }
            
            response.put("promptTokens", snapshot.getPromptTokens() != null ? snapshot.getPromptTokens() : 0);
            response.put("completionTokens", snapshot.getCompletionTokens() != null ? snapshot.getCompletionTokens() : 0);
            response.put("totalTokens", snapshot.getTotalTokens() != null ? snapshot.getTotalTokens() : 0);
            
            if (snapshot.getInputsStorageKey() != null) {
                response.put("inputsStorageKey", snapshot.getInputsStorageKey());
            }
            if (snapshot.getOutputsStorageKey() != null) {
                response.put("outputsStorageKey", snapshot.getOutputsStorageKey());
            }
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.toString());
                
        } catch (Exception e) {
            log.error("Failed to get node snapshot", e);
            ctx.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    /**
     * 列出执行历史
     */
    private void listExecutions(RoutingContext ctx) {
        if (snapshotManager == null) {
            ctx.response()
                .setStatusCode(503)
                .end(new JsonObject().put("error", "Snapshot manager not configured").encode());
            return;
        }
        
        try {
            String workflowId = ctx.request().getParam("workflowId");
            int offset = Integer.parseInt(ctx.request().getParam("offset", "0"));
            int limit = Integer.parseInt(ctx.request().getParam("limit", "50"));
            
            List<Map<String, Object>> executions = snapshotManager.listExecutions(workflowId, offset, limit);
            
            ObjectNode response = objectMapper.createObjectNode();
            ArrayNode executionsArray = response.putArray("executions");
            
            for (Map<String, Object> exec : executions) {
                ObjectNode execNode = executionsArray.addObject();
                execNode.put("id", (String) exec.get("id"));
                execNode.put("workflowId", (String) exec.get("workflowId"));
                execNode.put("status", (String) exec.get("status"));
                execNode.put("startTime", (Long) exec.get("startTime"));
                if (exec.get("endTime") != null) {
                    execNode.put("endTime", (Long) exec.get("endTime"));
                }
                if (exec.get("duration") != null) {
                    execNode.put("duration", (Long) exec.get("duration"));
                }
                if (exec.get("totalTokens") != null) {
                    execNode.put("totalTokens", (Integer) exec.get("totalTokens"));
                }
                if (exec.get("error") != null) {
                    execNode.put("error", (String) exec.get("error"));
                }
                execNode.put("createdAt", (Long) exec.get("createdAt"));
            }
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.toString());
        } catch (Exception e) {
            log.error("Failed to list executions", e);
            ctx.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", e.getMessage()).encode());
        }
    }
    
    /**
     * 列出节点类型
     */
    private void listNodeTypes(RoutingContext ctx) {
        ArrayNode response = objectMapper.createArrayNode();
        
        for (NodeType type : NodeType.values()) {
            ObjectNode typeNode = response.addObject();
            typeNode.put("code", type.getCode());
            typeNode.put("name", type.getName());
            typeNode.put("description", type.getDescription());
        }
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.toString());
    }
    
    /**
     * 解析工作流
     */
    private Graph parseWorkflow(JsonObject json, String workflowId) {
        String name = json.getString("name", "Untitled Workflow");
        
        Graph graph = new Graph(workflowId, name);
        
        // 解析节点
        if (json.containsKey("nodes")) {
            JsonObject nodesJson = json.getJsonObject("nodes");
            nodesJson.stream().forEach(entry -> {
                String nodeId = entry.getKey();
                JsonObject nodeJson = (JsonObject) entry.getValue();
                
                String nodeName = nodeJson.getString("name", nodeId);
                String nodeType = nodeJson.getString("type", "llm");
                JsonObject config = nodeJson.getJsonObject("config", new JsonObject());
                JsonObject position = nodeJson.getJsonObject("position", new JsonObject());
                
                Node node = NodeFactory.createNode(nodeId, nodeName, nodeType, config.getMap());
                
                // 设置位置
                if (position != null) {
                    Node.Position pos = new Node.Position(
                        position.getDouble("x", 0.0),
                        position.getDouble("y", 0.0)
                    );
                    node.setPosition(pos);
                }
                
                graph.addNode(node);
            });
        }
        
        // 解析边
        if (json.containsKey("edges")) {
            JsonArray edgesJson = json.getJsonArray("edges");
            for (int i = 0; i < edgesJson.size(); i++) {
                JsonObject edgeJson = edgesJson.getJsonObject(i);
                
                String from = edgeJson.getString("from");
                String to = edgeJson.getString("to");
                String type = edgeJson.getString("type", "normal");
                
                if ("conditional".equals(type)) {
                    JsonObject conditions = edgeJson.getJsonObject("conditions", new JsonObject());
                    String defaultTarget = edgeJson.getString("defaultTarget");
                    
                    Map<String, String> conditionMap = new HashMap<>();
                    conditions.stream().forEach(c -> {
                        conditionMap.put(c.getKey(), (String) c.getValue());
                    });
                    
                    graph.addConditionalEdge(from, conditionMap, defaultTarget);
                } else {
                    graph.addEdge(from, to);
                }
            }
        }
        
        // 设置入口点
        if (json.containsKey("entryPoint")) {
            graph.setEntryPoint(json.getString("entryPoint"));
        }
        
        return graph;
    }
    
    /**
     * 序列化工作流
     */
    private ObjectNode serializeWorkflow(Graph workflow) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", workflow.getId());
        node.put("name", workflow.getName());
        node.put("entryPoint", workflow.getEntryPoint());
        
        // 序列化节点
        ObjectNode nodesNode = node.putObject("nodes");
        for (Map.Entry<String, Node> entry : workflow.getNodes().entrySet()) {
            ObjectNode nodeJson = nodesNode.putObject(entry.getKey());
            Node n = entry.getValue();
            nodeJson.put("id", n.getId());
            nodeJson.put("name", n.getName());
            nodeJson.put("type", n.getType().getCode());
            nodeJson.set("config", objectMapper.valueToTree(n.getConfig()));
            
            if (n.getPosition() != null) {
                ObjectNode posNode = nodeJson.putObject("position");
                posNode.put("x", n.getPosition().getX());
                posNode.put("y", n.getPosition().getY());
            }
        }
        
        // 序列化边
        ArrayNode edgesNode = node.putArray("edges");
        for (Map.Entry<String, List<Edge>> entry : workflow.getEdges().entrySet()) {
            for (Edge edge : entry.getValue()) {
                ObjectNode edgeJson = edgesNode.addObject();
                edgeJson.put("id", edge.getFrom() + "-" + edge.getTo());
                edgeJson.put("from", edge.getFrom());
                edgeJson.put("to", edge.getTo());
                edgeJson.put("type", edge.getType().getCode());
                
                if (edge.getCondition() != null) {
                    edgeJson.set("conditions", objectMapper.valueToTree(edge.getCondition().getConditions()));
                    edgeJson.put("defaultTarget", edge.getCondition().getDefaultTarget());
                }
            }
        }
        
        return node;
    }
}
