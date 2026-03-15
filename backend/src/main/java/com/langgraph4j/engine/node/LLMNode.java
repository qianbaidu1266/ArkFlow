package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.model.LLMClient;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 节点
 * 调用大语言模型进行推理
 */
@Slf4j
public class LLMNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    
    public LLMNode(String id, String name) {
        super(id, name, NodeType.LLM);
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing LLM node: {}", id);
                
                // 获取配置
                String systemPrompt = getConfigValue("systemPrompt", "");
                String userPrompt = getConfigValue("userPrompt", "");
                String model = getConfigValue("model", null);
                Double temperature = getConfigValue("temperature", 0.7);
                Integer maxTokens = getConfigValue("maxTokens", 2000);
                String outputKey = getConfigValue("outputKey", "llm_output");
                
                // 渲染模板
                String renderedSystemPrompt = renderTemplate(systemPrompt, state);
                String renderedUserPrompt = renderTemplate(userPrompt, state);
                
                // 构建消息列表
                List<LLMClient.Message> messages = new ArrayList<>();
                
                if (!renderedSystemPrompt.isEmpty()) {
                    messages.add(LLMClient.Message.system(renderedSystemPrompt));
                }
                
                messages.add(LLMClient.Message.user(renderedUserPrompt));
                
                // 获取LLM客户端
                LLMClient llmClient = context.getLlmClient();
                if (llmClient == null) {
                    throw new RuntimeException("LLM client not configured");
                }
                
                // 构建参数
                LLMClient.ChatParams params = LLMClient.ChatParams.builder()
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();
                
                // 调用LLM
                LLMClient.ChatResponse response = llmClient.chat(messages, params).join();
                
                // 更新状态
                GraphState newState = state.copy();
                newState.set(outputKey, response.getContent());
                newState.set(outputKey + "_model", response.getModel());
                newState.set(outputKey + "_tokens", response.getTotalTokens());
                newState.set(outputKey + "_finish_reason", response.getFinishReason());
                
                log.debug("LLM node executed successfully: {}, tokens: {}", id, response.getTotalTokens());
                
                return newState;
                
            } catch (Exception e) {
                log.error("LLM node execution failed: {}", id, e);
                throw new RuntimeException("LLM node execution failed", e);
            }
        });
    }
    
    /**
     * 渲染模板，替换变量
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
        
        String userPrompt = getConfigValue("userPrompt", null);
        if (userPrompt == null || userPrompt.isEmpty()) {
            log.error("LLM node {}: userPrompt is required", id);
            return false;
        }
        
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        // 从模板中提取变量
        String userPrompt = getConfigValue("userPrompt", "");
        Matcher matcher = VARIABLE_PATTERN.matcher(userPrompt);
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            ParameterDef def = new ParameterDef();
            def.setName(varName);
            def.setType("string");
            def.setDescription("Template variable: " + varName);
            def.setRequired(true);
            params.put(varName, def);
        }
        
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String outputKey = getConfigValue("outputKey", "llm_output");
        
        ParameterDef contentDef = new ParameterDef();
        contentDef.setName(outputKey);
        contentDef.setType("string");
        contentDef.setDescription("LLM generated content");
        contentDef.setRequired(true);
        params.put(outputKey, contentDef);
        
        ParameterDef modelDef = new ParameterDef();
        modelDef.setName(outputKey + "_model");
        modelDef.setType("string");
        modelDef.setDescription("Model used for generation");
        modelDef.setRequired(false);
        params.put(outputKey + "_model", modelDef);
        
        ParameterDef tokensDef = new ParameterDef();
        tokensDef.setName(outputKey + "_tokens");
        tokensDef.setType("integer");
        tokensDef.setDescription("Total tokens used");
        tokensDef.setRequired(false);
        params.put(outputKey + "_tokens", tokensDef);
        
        return params;
    }
}
