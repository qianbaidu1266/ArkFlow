package com.langgraph4j.engine.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Agent 节点
 * 支持工具调用的智能体节点
 */
@Slf4j
public class AgentNode extends Node {
    
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
        "<tool_call>\\s*\\{([^}]+)\\}\\s*</tool_call>", Pattern.DOTALL);
    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
        "<think>(.*?)</think>", Pattern.DOTALL);
    
    private final ObjectMapper objectMapper;
    private final Map<String, Tool> tools;
    
    public AgentNode(String id, String name) {
        super(id, name, NodeType.AGENT);
        this.objectMapper = new ObjectMapper();
        this.tools = new HashMap<>();
    }
    
    /**
     * 注册工具
     */
    public void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
        log.debug("Tool registered: {}", tool.getName());
    }
    
    @Override
    public CompletableFuture<GraphState> execute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Agent node: {}", id);
                
                // 获取配置
                String systemPrompt = getConfigValue("systemPrompt", buildDefaultSystemPrompt());
                String userPrompt = getConfigValue("userPrompt", "");
                int maxIterations = getConfigValue("maxIterations", 5);
                String outputKey = getConfigValue("outputKey", "agent_output");
                
                // 渲染模板
                String renderedUserPrompt = renderTemplate(userPrompt, state);
                
                // 获取LLM客户端
                LLMClient llmClient = context.getLlmClient();
                if (llmClient == null) {
                    throw new RuntimeException("LLM client not configured");
                }
                
                // 构建消息列表
                List<LLMClient.Message> messages = new ArrayList<>();
                messages.add(LLMClient.Message.system(systemPrompt));
                messages.add(LLMClient.Message.user(renderedUserPrompt));
                
                String finalResponse = null;
                List<ToolCallResult> toolResults = new ArrayList<>();
                
                // Agent循环
                for (int i = 0; i < maxIterations; i++) {
                    log.debug("Agent iteration: {}/{}", i + 1, maxIterations);
                    
                    // 调用LLM
                    LLMClient.ChatResponse response = llmClient.chat(messages).join();
                    String content = response.getContent();
                    
                    // 提取思考过程
                    String thought = extractThought(content);
                    if (thought != null) {
                        log.debug("Agent thought: {}", thought);
                    }
                    
                    // 检查是否有工具调用
                    List<ToolCall> toolCalls = extractToolCalls(content);
                    
                    if (toolCalls.isEmpty()) {
                        // 没有工具调用，直接返回结果
                        finalResponse = content.replaceAll("<think>.*?</think>", "").trim();
                        break;
                    }
                    
                    // 执行工具调用
                    for (ToolCall toolCall : toolCalls) {
                        Tool tool = tools.get(toolCall.getName());
                        if (tool != null) {
                            log.debug("Executing tool: {} with params: {}", toolCall.getName(), toolCall.getParameters());
                            String result = tool.execute(toolCall.getParameters());
                            toolResults.add(new ToolCallResult(toolCall.getName(), result));
                            
                            // 添加工具结果到消息
                            messages.add(LLMClient.Message.assistant(content));
                            messages.add(LLMClient.Message.builder()
                                .role("tool")
                                .content(result)
                                .name(toolCall.getName())
                                .build());
                        } else {
                            log.warn("Tool not found: {}", toolCall.getName());
                        }
                    }
                }
                
                // 更新状态
                GraphState newState = state.copy();
                newState.set(outputKey, finalResponse);
                newState.set(outputKey + "_tool_calls", toolResults);
                newState.set(outputKey + "_iterations", toolResults.size());
                
                log.debug("Agent node executed successfully: {}", id);
                
                return newState;
                
            } catch (Exception e) {
                log.error("Agent node execution failed: {}", id, e);
                throw new RuntimeException("Agent node execution failed", e);
            }
        });
    }
    
    /**
     * 构建默认系统提示词
     */
    private String buildDefaultSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful AI assistant with access to tools.\n\n");
        sb.append("Available tools:\n");
        
        for (Tool tool : tools.values()) {
            sb.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
            sb.append("  Parameters: ").append(tool.getParametersSchema()).append("\n");
        }
        
        sb.append("\nWhen you need to use a tool, respond with:\n");
        sb.append("<tool_call>{\"name\": \"tool_name\", \"parameters\": {...}}</tool_call>\n\n");
        sb.append("You can use <think>your reasoning</think> to show your thinking process.\n\n");
        sb.append("If no tool is needed, respond directly to the user.");
        
        return sb.toString();
    }
    
    /**
     * 提取思考过程
     */
    private String extractThought(String content) {
        Matcher matcher = THOUGHT_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    /**
     * 提取工具调用
     */
    private List<ToolCall> extractToolCalls(String content) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                String jsonStr = "{" + matcher.group(1) + "}";
                JsonNode jsonNode = objectMapper.readTree(jsonStr);
                
                String name = jsonNode.get("name").asText();
                JsonNode paramsNode = jsonNode.get("parameters");
                Map<String, Object> params = objectMapper.convertValue(paramsNode, Map.class);
                
                toolCalls.add(new ToolCall(name, params));
            } catch (Exception e) {
                log.warn("Failed to parse tool call: {}", matcher.group(1), e);
            }
        }
        
        return toolCalls;
    }
    
    /**
     * 渲染模板
     */
    private String renderTemplate(String template, GraphState state) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        Pattern pattern = Pattern.compile("\\{\\{(\\w+)\\}\\}");
        Matcher matcher = pattern.matcher(template);
        StringBuffer result = new StringBuffer();
        
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
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        return params;
    }
    
    @Override
    public Map<String, ParameterDef> getOutputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String outputKey = getConfigValue("outputKey", "agent_output");
        
        ParameterDef contentDef = new ParameterDef();
        contentDef.setName(outputKey);
        contentDef.setType("string");
        contentDef.setDescription("Agent output");
        contentDef.setRequired(true);
        params.put(outputKey, contentDef);
        
        return params;
    }
    
    // 工具接口
    public interface Tool {
        String getName();
        String getDescription();
        String getParametersSchema();
        String execute(Map<String, Object> parameters);
    }
    
    // 工具调用
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ToolCall {
        private String name;
        private Map<String, Object> parameters;
    }
    
    // 工具调用结果
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ToolCallResult {
        private String toolName;
        private String result;
    }
}
