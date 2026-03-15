package com.langgraph4j.engine.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Embedding 客户端
 * 支持多种Embedding提供商的自定义接入
 */
@Slf4j
public class EmbeddingClient {
    
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Integer dimensions;
    private final Map<String, Object> defaultParams;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    
    public EmbeddingClient(String baseUrl, String apiKey, String model, 
                          Integer dimensions, Map<String, Object> defaultParams) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dimensions = dimensions;
        this.defaultParams = defaultParams;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    /**
     * 获取单个文本的embedding
     */
    public CompletableFuture<float[]> embed(String text) {
        return embedBatch(List.of(text))
            .thenApply(embeddings -> embeddings.isEmpty() ? null : embeddings.get(0));
    }
    
    /**
     * 批量获取embedding
     */
    public CompletableFuture<List<float[]>> embedBatch(List<String> texts) {
        return CompletableFuture.supplyAsync(() -> doEmbedBatch(texts), executorService);
    }
    
    /**
     * 获取查询文本的embedding（可能使用不同的模型或参数）
     */
    public CompletableFuture<float[]> embedQuery(String text) {
        return embed(text);
    }
    
    /**
     * 获取文档文本的embedding（可能使用不同的模型或参数）
     */
    public CompletableFuture<List<float[]>> embedDocuments(List<String> texts) {
        return embedBatch(texts);
    }
    
    private List<float[]> doEmbedBatch(List<String> texts) {
        try {
            HttpPost request = buildRequest(texts);
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                
                return parseResponse(jsonNode);
            }
        } catch (Exception e) {
            log.error("Embedding request failed", e);
            throw new RuntimeException("Embedding request failed", e);
        }
    }
    
    private HttpPost buildRequest(List<String> texts) {
        HttpPost request = new HttpPost(baseUrl + "/embeddings");
        request.setHeader("Authorization", "Bearer " + apiKey);
        request.setHeader("Content-Type", "application/json");
        
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        
        // 添加输入文本
        if (texts.size() == 1) {
            requestBody.put("input", texts.get(0));
        } else {
            ArrayNode inputArray = requestBody.putArray("input");
            for (String text : texts) {
                inputArray.add(text);
            }
        }
        
        // 添加维度参数
        if (dimensions != null) {
            requestBody.put("dimensions", dimensions);
        }
        
        // 默认参数
        if (defaultParams != null) {
            defaultParams.forEach((k, v) -> {
                requestBody.set(k, objectMapper.valueToTree(v));
            });
        }
        
        request.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));
        return request;
    }
    
    private List<float[]> parseResponse(JsonNode jsonNode) {
        List<float[]> embeddings = new ArrayList<>();
        
        if (jsonNode.has("data") && jsonNode.get("data").isArray()) {
            ArrayNode dataArray = (ArrayNode) jsonNode.get("data");
            
            for (JsonNode item : dataArray) {
                if (item.has("embedding")) {
                    JsonNode embeddingNode = item.get("embedding");
                    float[] embedding = parseEmbeddingArray(embeddingNode);
                    embeddings.add(embedding);
                }
            }
        }
        
        return embeddings;
    }
    
    private float[] parseEmbeddingArray(JsonNode embeddingNode) {
        if (embeddingNode.isArray()) {
            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }
            return embedding;
        }
        return new float[0];
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        
        float dotProduct = 0;
        float normA = 0;
        float normB = 0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
    
    /**
     * 计算两个向量的欧氏距离
     */
    public static float euclideanDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        
        return (float) Math.sqrt(sum);
    }
    
    /**
     * 关闭客户端
     */
    public void close() throws IOException {
        httpClient.close();
        executorService.shutdown();
    }
    
    // Embedding响应
    @Data
    public static class EmbeddingResponse {
        private List<float[]> embeddings;
        private String model;
        private int totalTokens;
    }
}
