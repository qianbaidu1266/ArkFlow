package com.langgraph4j.engine.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntityContainer;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 请求节点
 * 发送HTTP请求并处理响应
 */
@Slf4j
public class HttpNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    
    public HttpNode(String id, String name) {
        super(id, name, NodeType.HTTP);
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClients.createDefault();
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing HTTP node: {}", id);
                
                // 获取配置
                String url = getConfigValue("url", "");
                String method = getConfigValue("method", "GET").toUpperCase();
                Map<String, String> headers = getConfigValue("headers", new HashMap<>());
                Object body = getConfigValue("body", null);
                String bodyVariable = getConfigValue("bodyVariable", null);
                int timeout = getConfigValue("timeout", 30000);
                String outputKey = getConfigValue("outputKey", "http_response");
                boolean extractJson = getConfigValue("extractJson", true);
                String jsonPath = getConfigValue("jsonPath", null);
                
                // 渲染URL
                String renderedUrl = renderTemplate(url, state);
                
                // 渲染headers
                Map<String, String> renderedHeaders = new HashMap<>();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    renderedHeaders.put(
                        renderTemplate(entry.getKey(), state),
                        renderTemplate(entry.getValue(), state)
                    );
                }
                
                // 构建请求体
                String requestBody = null;
                if (bodyVariable != null) {
                    Object bodyValue = state.get(bodyVariable);
                    if (bodyValue != null) {
                        requestBody = objectMapper.writeValueAsString(bodyValue);
                    }
                } else if (body != null) {
                    if (body instanceof String) {
                        requestBody = renderTemplate((String) body, state);
                    } else {
                        requestBody = objectMapper.writeValueAsString(body);
                    }
                }
                
                // 发送请求
                String response = sendRequest(renderedUrl, method, renderedHeaders, requestBody, timeout);
                
                // 处理响应
                Object output = response;
                if (extractJson) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(response);
                        if (jsonPath != null && !jsonPath.isEmpty()) {
                            output = extractJsonPath(jsonNode, jsonPath);
                        } else {
                            output = jsonNode;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse response as JSON: {}", e.getMessage());
                        output = response;
                    }
                }
                
                // 更新状态
                GraphState newState = state.copy();
                newState.set(outputKey, output);
                newState.set(outputKey + "_raw", response);
                
                log.debug("HTTP node executed successfully: {}", id);
                
                return newState;
                
            } catch (Exception e) {
                log.error("HTTP node execution failed: {}", id, e);
                throw new RuntimeException("HTTP node execution failed", e);
            }
        });
    }
    
    private String sendRequest(String url, String method, Map<String, String> headers, 
                               String body, int timeout) throws IOException, ParseException {
        HttpUriRequestBase request;
        
        switch (method) {
            case "POST":
                request = new HttpPost(url);
                break;
            case "PUT":
                request = new HttpPut(url);
                break;
            case "PATCH":
                request = new HttpPatch(url);
                break;
            case "DELETE":
                request = new HttpDelete(url);
                break;
            case "GET":
            default:
                request = new HttpGet(url);
                break;
        }
        
        // 设置headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.setHeader(entry.getKey(), entry.getValue());
        }
        
        // 设置body
        if (body != null && (request instanceof HttpPost || request instanceof HttpPut || request instanceof HttpPatch)) {
            ((HttpEntityContainer) request).setEntity(
                new StringEntity(body, ContentType.APPLICATION_JSON)
            );
        }
        
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return EntityUtils.toString(response.getEntity());
        }
    }
    
    private Object extractJsonPath(JsonNode node, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = node;
        
        for (String part : parts) {
            if (current == null || current.isNull()) {
                return null;
            }
            
            // 处理数组索引
            if (part.matches(".*\\[\\d+\\]$")) {
                String fieldName = part.substring(0, part.indexOf('['));
                int index = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.indexOf(']')));
                
                if (!fieldName.isEmpty()) {
                    current = current.get(fieldName);
                }
                
                if (current != null && current.isArray()) {
                    current = current.get(index);
                }
            } else {
                current = current.get(part);
            }
        }
        
        if (current == null || current.isNull()) {
            return null;
        }
        
        if (current.isTextual()) {
            return current.asText();
        } else if (current.isNumber()) {
            return current.numberValue();
        } else if (current.isBoolean()) {
            return current.asBoolean();
        } else {
            return current;
        }
    }
    
    private String renderTemplate(String template, GraphState state) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        StringBuilder result = new StringBuilder();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object varValue = state.get(varName);
            String replacement = varValue != null ? varValue.toString() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    @Override
    public boolean validate() {
        if (config == null) return false;
        
        String url = getConfigValue("url", null);
        if (url == null || url.isEmpty()) {
            log.error("HTTP node {}: url is required", id);
            return false;
        }
        
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String bodyVariable = getConfigValue("bodyVariable", null);
        if (bodyVariable != null) {
            ParameterDef def = new ParameterDef();
            def.setName(bodyVariable);
            def.setType("any");
            def.setDescription("Request body variable");
            def.setRequired(false);
            params.put(bodyVariable, def);
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String outputKey = getConfigValue("outputKey", "http_response");
        
        ParameterDef responseDef = new ParameterDef();
        responseDef.setName(outputKey);
        responseDef.setType("any");
        responseDef.setDescription("HTTP response");
        responseDef.setRequired(true);
        params.put(outputKey, responseDef);
        
        return params;
    }
}
