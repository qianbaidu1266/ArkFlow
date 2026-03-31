package com.langgraph4j.engine.websocket;

import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketHandler implements Handler<ServerWebSocket> {
    
    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    private final Map<String, ServerWebSocket> connections = new ConcurrentHashMap<>();
    private final ExecutionEventBus eventBus;
    
    public WebSocketHandler(ExecutionEventBus eventBus) {
        this.eventBus = eventBus;
        subscribeToEvents();
    }
    
    private void subscribeToEvents() {
        eventBus.getEventBus().consumer("execution\\..*", message -> {
            String address = message.address();
            String executionId = address.replace("execution.", "");
            
            JsonObject data = (JsonObject) message.body();
            broadcastToExecution(executionId, data.encode());
        });
    }
    
    @Override
    public void handle(ServerWebSocket ws) {
        String connectionId = ws.textHandlerID();
        connections.put(connectionId, ws);
        log.info("WebSocket connected: {}", connectionId);
        
        ws.frameHandler(frame -> handleFrame(ws, frame));
        
        ws.closeHandler(v -> {
            connections.remove(connectionId);
            log.info("WebSocket disconnected: {}", connectionId);
        });
        
        ws.exceptionHandler(e -> {
            log.error("WebSocket error: {}", connectionId, e);
            connections.remove(connectionId);
        });
        
        ws.writeTextMessage(new JsonObject()
            .put("type", "connected")
            .put("connectionId", connectionId)
            .encode()
        );
    }
    
    private void handleFrame(ServerWebSocket ws, WebSocketFrame frame) {
        if (!frame.isText()) {
            return;
        }
        
        try {
            String text = frame.textData();
            JsonObject message = new JsonObject(text);
            String type = message.getString("type");
            
            switch (type) {
                case "subscribe":
                    handleSubscribe(ws, message);
                    break;
                case "unsubscribe":
                    handleUnsubscribe(ws, message);
                    break;
                case "ping":
                    ws.writeTextMessage(new JsonObject().put("type", "pong").encode());
                    break;
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to handle WebSocket message", e);
            ws.writeTextMessage(new JsonObject()
                .put("type", "error")
                .put("message", "Invalid message format")
                .encode()
            );
        }
    }
    
    private void handleSubscribe(ServerWebSocket ws, JsonObject message) {
        String executionId = message.getString("executionId");
        if (executionId == null || executionId.isEmpty()) {
            ws.writeTextMessage(new JsonObject()
                .put("type", "error")
                .put("message", "executionId is required")
                .encode()
            );
            return;
        }
        
        String connectionId = ws.textHandlerID();
        ws.writeTextMessage(new JsonObject()
            .put("type", "subscribed")
            .put("executionId", executionId)
            .encode()
        );
        
        log.info("Client {} subscribed to execution: {}", connectionId, executionId);
    }
    
    private void handleUnsubscribe(ServerWebSocket ws, JsonObject message) {
        String executionId = message.getString("executionId");
        String connectionId = ws.textHandlerID();
        
        ws.writeTextMessage(new JsonObject()
            .put("type", "unsubscribed")
            .put("executionId", executionId)
            .encode()
        );
        
        log.info("Client {} unsubscribed from execution: {}", connectionId, executionId);
    }
    
    public void broadcastToExecution(String executionId, String message) {
        connections.values().forEach(ws -> {
            try {
                ws.writeTextMessage(message);
            } catch (Exception e) {
                log.error("Failed to send message to client", e);
            }
        });
    }
    
    public int getConnectionCount() {
        return connections.size();
    }
}
