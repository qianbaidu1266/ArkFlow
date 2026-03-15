package com.langgraph4j.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LLM 客户端
 * 支持多种LLM提供商的自定义接入
 */
@Slf4j
public class LLMClient {
    
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Map<String, Object> defaultParams;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    
    public LLMClient(String baseUrl, String apiKey, String model, Map<String, Object> defaultParams) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.defaultParams = defaultParams;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    /**
     * 发送聊天请求
     */
    public CompletableFuture<ChatResponse> chat(List<Message> messages) {
        return CompletableFuture.supplyAsync(() -> doChat(messages, null), executorService);
    }
    
    /**
     * 发送聊天请求（带参数）
     */
    public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatParams params) {
        return CompletableFuture.supplyAsync(() -> doChat(messages, params), executorService);
    }
    
    /**
     * 流式聊天
     */
    public CompletableFuture<Void> chatStream(List<Message> messages, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> doChatStream(messages, callback), executorService);
    }
    
    private ChatResponse doChat(List<Message> messages, ChatParams params) {
        try {
            HttpPost request = buildRequest(messages, params, false);
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                
                return parseResponse(jsonNode);
            }
        } catch (Exception e) {
            log.error("LLM chat request failed", e);
            throw new RuntimeException("LLM chat request failed", e);
        }
    }
    
    private void doChatStream(List<Message> messages, StreamCallback callback) {
        try {
            HttpPost request = buildRequest(messages, null, true);
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                // 处理流式响应
                // 这里简化处理，实际实现需要解析SSE格式
                String responseBody = EntityUtils.toString(response.getEntity());
                callback.onComplete(responseBody);
            }
        } catch (Exception e) {
            log.error("LLM stream request failed", e);
            callback.onError(e);
        }
    }
    
    private HttpPost buildRequest(List<Message> messages, ChatParams params, boolean stream) {
        HttpPost request = new HttpPost(baseUrl + "/chat/completions");
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setHeader("Content-Type", "application/json");
        
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        
        // 添加消息
        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Message message : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", message.getRole());
            msgNode.put("content", message.getContent());
            if (message.getName() != null) {
                msgNode.put("name", message.getName());
            }
        }
        
        // 添加参数
        if (params != null) {
            if (params.getTemperature() != null) {
                requestBody.put("temperature", params.getTemperature());
            }
            if (params.getMaxTokens() != null) {
                requestBody.put("max_tokens", params.getMaxTokens());
            }
            if (params.getTopP() != null) {
                requestBody.put("top_p", params.getTopP());
            }
        }
        
        // 默认参数
        if (defaultParams != null) {
            defaultParams.forEach((k, v) -> {
                if (!requestBody.has(k)) {
                    requestBody.set(k, objectMapper.valueToTree(v));
                }
            });
        }
        
        if (stream) {
            requestBody.put("stream", true);
        }
        
        request.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));
        return request;
    }
    
    private ChatResponse parseResponse(JsonNode jsonNode) {
        ChatResponse response = new ChatResponse();
        
        if (jsonNode.has("choices") && jsonNode.get("choices").isArray()) {
            JsonNode choice = jsonNode.get("choices").get(0);
            if (choice.has("message")) {
                JsonNode message = choice.get("message");
                response.setContent(message.get("content").asText());
                response.setRole(message.get("role").asText());
            }
            if (choice.has("finish_reason")) {
                response.setFinishReason(choice.get("finish_reason").asText());
            }
        }
        
        if (jsonNode.has("usage")) {
            JsonNode usage = jsonNode.get("usage");
            response.setPromptTokens(usage.get("prompt_tokens").asInt());
            response.setCompletionTokens(usage.get("completion_tokens").asInt());
            response.setTotalTokens(usage.get("total_tokens").asInt());
        }
        
        if (jsonNode.has("model")) {
            response.setModel(jsonNode.get("model").asText());
        }
        
        return response;
    }
    
    /**
     * 关闭客户端
     */
    public void close() throws IOException {
        httpClient.close();
        executorService.shutdown();
    }
    
    // 消息类
    @Data
    @Builder
    public static class Message {
        private String role;  // system, user, assistant, tool
        private String content;
        private String name;
        
        public static Message system(String content) {
            return Message.builder().role("system").content(content).build();
        }
        
        public static Message user(String content) {
            return Message.builder().role("user").content(content).build();
        }
        
        public static Message assistant(String content) {
            return Message.builder().role("assistant").content(content).build();
        }
    }
    
    // 聊天参数
    @Data
    @Builder
    public static class ChatParams {
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Double frequencyPenalty;
        private Double presencePenalty;
    }
    
    // 聊天响应
    @Data
    public static class ChatResponse {
        private String content;
        private String role;
        private String finishReason;
        private String model;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
    
    // 流式回调
    public interface StreamCallback {
        void onMessage(String chunk);
        void onComplete(String fullResponse);
        void onError(Throwable error);
    }
}
