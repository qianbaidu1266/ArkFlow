package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.rag.KnowledgeBase;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识检索节点
 * 从知识库检索相关内容
 */
@Slf4j
public class KnowledgeRetrievalNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    
    public KnowledgeRetrievalNode(String id, String name) {
        super(id, name, NodeType.KNOWLEDGE_RETRIEVAL);
    }
    
    @Override
    public java.util.concurrent.CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Knowledge Retrieval node: {}", id);
                
                // 获取配置
                String knowledgeBaseId = getConfigValue("knowledgeBaseId", null);
                String queryTemplate = getConfigValue("query", "");
                String queryVariable = getConfigValue("queryVariable", null);
                int topK = getConfigValue("topK", 5);
                float scoreThreshold = getConfigValue("scoreThreshold", 0.7f);
                String searchType = getConfigValue("searchType", "similarity");
                String outputKey = getConfigValue("outputKey", "retrieved_context");
                String outputFormat = getConfigValue("outputFormat", "text");  // text, json, chunks
                
                // 构建查询
                String query;
                if (queryVariable != null) {
                    Object queryValue = state.get(queryVariable);
                    query = queryValue != null ? queryValue.toString() : "";
                } else {
                    query = renderTemplate(queryTemplate, state);
                }
                
                if (query.isEmpty()) {
                    log.warn("Empty query for knowledge retrieval node: {}", id);
                    GraphState newState = state.copy();
                    newState.set(outputKey, "");
                    newState.set(outputKey + "_chunks", List.of());
                    return newState;
                }
                
                // 获取知识库
                KnowledgeBase knowledgeBase = context.getKnowledgeBase();
                if (knowledgeBase == null) {
                    throw new RuntimeException("Knowledge base not configured");
                }
                
                // 构建检索配置
                KnowledgeBase.RetrievalConfig retrievalConfig = KnowledgeBase.RetrievalConfig.builder()
                    .topK(topK)
                    .scoreThreshold(scoreThreshold)
                    .searchType(searchType)
                    .build();
                
                // 执行检索
                List<KnowledgeBase.DocumentChunk> chunks = knowledgeBase.retrieve(query, retrievalConfig).join();
                
                // 过滤低分结果
                chunks = chunks.stream()
                    .filter(chunk -> chunk.getScore() >= scoreThreshold)
                    .collect(Collectors.toList());
                
                // 格式化输出
                Object output = formatOutput(chunks, outputFormat);
                
                // 更新状态
                GraphState newState = state.copy();
                newState.set(outputKey, output);
                newState.set(outputKey + "_chunks", chunks);
                newState.set(outputKey + "_query", query);
                newState.set(outputKey + "_count", chunks.size());
                
                log.debug("Knowledge retrieval completed: {}, found {} chunks", id, chunks.size());
                
                return newState;
                
            } catch (Exception e) {
                log.error("Knowledge retrieval node execution failed: {}", id, e);
                throw new RuntimeException("Knowledge retrieval node execution failed", e);
            }
        });
    }
    
    /**
     * 格式化输出
     */
    private Object formatOutput(List<KnowledgeBase.DocumentChunk> chunks, String format) {
        switch (format) {
            case "json":
                return chunks.stream()
                    .map(chunk -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("content", chunk.getContent());
                        map.put("score", chunk.getScore());
                        map.put("documentId", chunk.getDocumentId());
                        map.put("metadata", chunk.getMetadata());
                        return map;
                    })
                    .collect(Collectors.toList());
                    
            case "chunks":
                return chunks;
                
            case "text":
            default:
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < chunks.size(); i++) {
                    KnowledgeBase.DocumentChunk chunk = chunks.get(i);
                    sb.append("[").append(i + 1).append("] ");
                    sb.append(chunk.getContent());
                    if (i < chunks.size() - 1) {
                        sb.append("\n\n");
                    }
                }
                return sb.toString();
        }
    }
    
    /**
     * 渲染模板
     */
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
        
        String knowledgeBaseId = getConfigValue("knowledgeBaseId", null);
        if (knowledgeBaseId == null || knowledgeBaseId.isEmpty()) {
            log.error("Knowledge retrieval node {}: knowledgeBaseId is required", id);
            return false;
        }
        
        String query = getConfigValue("query", null);
        String queryVariable = getConfigValue("queryVariable", null);
        
        if ((query == null || query.isEmpty()) && (queryVariable == null || queryVariable.isEmpty())) {
            log.error("Knowledge retrieval node {}: query or queryVariable is required", id);
            return false;
        }
        
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String queryVariable = getConfigValue("queryVariable", null);
        if (queryVariable != null) {
            ParameterDef def = new ParameterDef();
            def.setName(queryVariable);
            def.setType("string");
            def.setDescription("Query variable for knowledge retrieval");
            def.setRequired(true);
            params.put(queryVariable, def);
        }
        
        // 从模板中提取变量
        String queryTemplate = getConfigValue("query", "");
        Matcher matcher = VARIABLE_PATTERN.matcher(queryTemplate);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (!params.containsKey(varName)) {
                ParameterDef def = new ParameterDef();
                def.setName(varName);
                def.setType("string");
                def.setDescription("Template variable: " + varName);
                def.setRequired(true);
                params.put(varName, def);
            }
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String outputKey = getConfigValue("outputKey", "retrieved_context");
        String outputFormat = getConfigValue("outputFormat", "text");
        
        ParameterDef contentDef = new ParameterDef();
        contentDef.setName(outputKey);
        contentDef.setType("string".equals(outputFormat) ? "string" : "array");
        contentDef.setDescription("Retrieved context");
        contentDef.setRequired(true);
        params.put(outputKey, contentDef);
        
        ParameterDef chunksDef = new ParameterDef();
        chunksDef.setName(outputKey + "_chunks");
        chunksDef.setType("array");
        chunksDef.setDescription("Retrieved document chunks");
        chunksDef.setRequired(false);
        params.put(outputKey + "_chunks", chunksDef);
        
        ParameterDef countDef = new ParameterDef();
        countDef.setName(outputKey + "_count");
        countDef.setType("integer");
        countDef.setDescription("Number of retrieved chunks");
        countDef.setRequired(false);
        params.put(outputKey + "_count", countDef);
        
        return params;
    }
}
