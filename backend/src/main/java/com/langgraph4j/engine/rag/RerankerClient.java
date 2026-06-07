package com.langgraph4j.engine.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.langgraph4j.engine.model.EmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Reranker 客户端
 * 支持调用 Reranker 模型对检索结果进行重排序
 * 兼容 Jina Reranker / BGE Reranker / Cohere Reranker / 自部署 Reranker API
 */
@Slf4j
public class RerankerClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    public RerankerClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(4);
    }

    /**
     * 对检索结果进行重排序
     *
     * @param query   查询文本
     * @param chunks  候选文档块
     * @param topK    返回 topK 结果
     * @return 重排序后的文档块
     */
    public CompletableFuture<List<KnowledgeBase.DocumentChunk>> rerank(
            String query, List<KnowledgeBase.DocumentChunk> chunks, int topK) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> documents = chunks.stream()
                        .map(KnowledgeBase.DocumentChunk::getContent)
                        .collect(Collectors.toList());

                HttpPost request = new HttpPost(baseUrl + "/rerank");
                request.setHeader("Authorization", "Bearer " + apiKey);
                request.setHeader("Content-Type", "application/json");

                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", model);
                requestBody.put("query", query);
                ArrayNode docsArray = requestBody.putArray("documents");
                for (String doc : documents) {
                    docsArray.add(doc);
                }
                requestBody.put("top_n", Math.min(topK, documents.size()));
                requestBody.put("return_documents", false);

                request.setEntity(new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON));

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode jsonNode = objectMapper.readTree(responseBody);

                    List<KnowledgeBase.DocumentChunk> reranked = new ArrayList<>();
                    if (jsonNode.has("results") && jsonNode.get("results").isArray()) {
                        for (JsonNode result : jsonNode.get("results")) {
                            int index = result.get("index").asInt();
                            float score = (float) result.get("relevance_score").asDouble();

                            KnowledgeBase.DocumentChunk original = chunks.get(index);
                            KnowledgeBase.DocumentChunk rerankedChunk = KnowledgeBase.DocumentChunk.builder()
                                    .id(original.getId())
                                    .documentId(original.getDocumentId())
                                    .content(original.getContent())
                                    .chunkIndex(original.getChunkIndex())
                                    .score(score)
                                    .metadata(original.getMetadata())
                                    .build();
                            reranked.add(rerankedChunk);
                        }
                    }

                    log.debug("Reranked {} chunks to {} results", chunks.size(), reranked.size());
                    return reranked;
                }
            } catch (Exception e) {
                log.error("Reranker request failed, returning original results", e);
                return chunks.stream().limit(topK).collect(Collectors.toList());
            }
        }, executorService);
    }

    public void close() {
        try {
            httpClient.close();
            executorService.shutdown();
        } catch (Exception e) {
            log.error("Failed to close reranker client", e);
        }
    }
}
