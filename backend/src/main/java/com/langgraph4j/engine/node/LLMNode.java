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

@Slf4j
public class LLMNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    
    public LLMNode(String id, String name) {
        super(id, name, NodeType.LLM);
    }
    
    @Override
    protected CompletableFuture<GraphState> doExecute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing LLM node: {}", id);
                
                String systemPrompt = getConfigValue("systemPrompt", "");
                String userPrompt = getConfigValue("userPrompt", "");
                Double temperature = getConfigValue("temperature", 0.7);
                Integer maxTokens = getConfigValue("maxTokens", 2000);
                String outputKey = getConfigValue("outputKey", "llm_output");
                
                String renderedSystemPrompt = renderTemplate(systemPrompt, state);
                String renderedUserPrompt = renderTemplate(userPrompt, state);
                
                List<LLMClient.Message> messages = new ArrayList<>();
                
                if (!renderedSystemPrompt.isEmpty()) {
                    messages.add(LLMClient.Message.system(renderedSystemPrompt));
                }
                
                messages.add(LLMClient.Message.user(renderedUserPrompt));
                
                LLMClient llmClient = context.getLlmClient();
                if (llmClient == null) {
                    throw new RuntimeException("LLM client not configured");
                }
                
                LLMClient.ChatParams params = LLMClient.ChatParams.builder()
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();
                
                LLMClient.ChatResponse response = llmClient.chat(messages, params).join();
                
                GraphState newState = state.copy();
                newState.set(outputKey, response.getContent());
                newState.set(outputKey + "_model", response.getModel());
                newState.set(outputKey + "_tokens", response.getTotalTokens());
                newState.set(outputKey + "_finish_reason", response.getFinishReason());
                newState.set("_llm_prompt_tokens", response.getPromptTokens());
                newState.set("_llm_completion_tokens", response.getCompletionTokens());
                
                log.debug("LLM node executed successfully: {}, tokens: {}", id, response.getTotalTokens());
                
                return newState;
                
            } catch (Exception e) {
                log.error("LLM node execution failed: {}", id, e);
                throw new RuntimeException("LLM node execution failed", e);
            }
        });
    }
    
    @Override
    protected Map<String, Object> extractInputs(GraphState state) {
        Map<String, Object> inputs = new HashMap<>();
        
        String systemPrompt = getConfigValue("systemPrompt", "");
        String userPrompt = getConfigValue("userPrompt", "");
        
        if (!systemPrompt.isEmpty()) {
            inputs.put("systemPrompt", renderTemplate(systemPrompt, state));
        }
        inputs.put("userPrompt", renderTemplate(userPrompt, state));
        
        return inputs;
    }
    
    @Override
    protected Map<String, Object> extractOutputs(GraphState state) {
        Map<String, Object> outputs = new HashMap<>();
        String outputKey = getConfigValue("outputKey", "llm_output");
        
        if (state.contains(outputKey)) {
            outputs.put("content", state.get(outputKey));
        }
        
        return outputs;
    }
    
    @Override
    protected Map<String, Object> buildMetadata(GraphState input, GraphState output, ExecutionContext context) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("model", getConfigValue("model", "default"));
        metadata.put("temperature", getConfigValue("temperature", 0.7));
        metadata.put("maxTokens", getConfigValue("maxTokens", 2000));
        
        String outputKey = getConfigValue("outputKey", "llm_output");
        if (output.contains(outputKey + "_finish_reason")) {
            metadata.put("finishReason", output.get(outputKey + "_finish_reason"));
        }
        
        return metadata;
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
        
        return params;
    }
}
