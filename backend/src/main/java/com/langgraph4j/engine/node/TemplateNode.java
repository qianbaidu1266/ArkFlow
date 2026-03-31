package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.ExecutionContext;
import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import com.langgraph4j.engine.state.GraphState;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class TemplateNode extends Node {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");
    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\{%\\s*if\\s+(\\w+)\\s*%\\}(.*?)(?:\\{%\\s*else\\s*%\\}(.*?))?\\{%\\s*endif\\s*%\\}", Pattern.DOTALL);
    private static final Pattern LOOP_PATTERN = Pattern.compile("\\{%\\s*for\\s+(\\w+)\\s+in\\s+(\\w+)\\s*%\\}(.*?)\\{%\\s*endfor\\s*%\\}", Pattern.DOTALL);
    
    public TemplateNode(String id, String name) {
        super(id, name, NodeType.TEMPLATE);
    }
    
    @Override
    protected CompletableFuture<GraphState> doExecute(GraphState state, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Executing Template node: {}", id);
                
                String template = getConfigValue("template", "");
                String outputKey = getConfigValue("outputKey", "rendered_template");
                String outputFormat = getConfigValue("outputFormat", "text");
                
                String result = renderTemplate(template, state);
                
                switch (outputFormat) {
                    case "markdown":
                        result = formatMarkdown(result);
                        break;
                    case "html":
                        result = formatHtml(result);
                        break;
                    default:
                        break;
                }
                
                GraphState newState = state.copy();
                newState.set(outputKey, result);
                
                log.debug("Template node executed successfully: {}", id);
                
                return newState;
                
            } catch (Exception e) {
                log.error("Template node execution failed: {}", id, e);
                throw new RuntimeException("Template node execution failed", e);
            }
        });
    }
    
    @Override
    protected Map<String, Object> extractInputs(GraphState state) {
        Map<String, Object> inputs = new HashMap<>();
        String template = getConfigValue("template", "");
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String varName = matcher.group(1);
            if (state.contains(varName)) {
                inputs.put(varName, state.get(varName));
            }
        }
        return inputs;
    }
    
    @Override
    protected Map<String, Object> extractOutputs(GraphState state) {
        Map<String, Object> outputs = new HashMap<>();
        String outputKey = getConfigValue("outputKey", "rendered_template");
        if (state.contains(outputKey)) {
            outputs.put("rendered", state.get(outputKey));
        }
        return outputs;
    }
    
    private String renderTemplate(String template, GraphState state) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        String result = template;
        
        result = renderLoops(result, state);
        result = renderConditions(result, state);
        result = renderVariables(result, state);
        
        return result;
    }
    
    private String renderVariables(String template, GraphState state) {
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
    
    private String renderConditions(String template, GraphState state) {
        String result = template;
        Matcher matcher = CONDITION_PATTERN.matcher(result);
        
        while (matcher.find()) {
            String conditionVar = matcher.group(1);
            String trueContent = matcher.group(2);
            String falseContent = matcher.group(3);
            
            Object value = state.get(conditionVar);
            boolean condition = value != null && (
                (value instanceof Boolean && (Boolean) value) ||
                (value instanceof String && !((String) value).isEmpty()) ||
                (value instanceof Number && ((Number) value).doubleValue() != 0)
            );
            
            String replacement = condition ? trueContent : (falseContent != null ? falseContent : "");
            result = result.replace(matcher.group(0), replacement);
            
            matcher = CONDITION_PATTERN.matcher(result);
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    private String renderLoops(String template, GraphState state) {
        String result = template;
        Matcher matcher = LOOP_PATTERN.matcher(result);
        
        while (matcher.find()) {
            String itemVar = matcher.group(1);
            String listVar = matcher.group(2);
            String loopContent = matcher.group(3);
            
            Object listValue = state.get(listVar);
            if (listValue instanceof java.util.List) {
                java.util.List<Object> list = (java.util.List<Object>) listValue;
                StringBuilder loopResult = new StringBuilder();
                
                for (Object item : list) {
                    String itemContent = loopContent;
                    
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        for (Map.Entry<String, Object> entry : itemMap.entrySet()) {
                            itemContent = itemContent.replace(
                                "{{" + itemVar + "." + entry.getKey() + "}}",
                                entry.getValue() != null ? entry.getValue().toString() : ""
                            );
                        }
                    } else {
                        itemContent = itemContent.replace(
                            "{{" + itemVar + "}}",
                            item != null ? item.toString() : ""
                        );
                    }
                    
                    loopResult.append(itemContent);
                }
                
                result = result.replace(matcher.group(0), loopResult.toString());
            } else {
                result = result.replace(matcher.group(0), "");
            }
            
            matcher = LOOP_PATTERN.matcher(result);
        }
        
        return result;
    }
    
    private String formatMarkdown(String text) {
        return text.trim();
    }
    
    private String formatHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("\n", "<br>");
    }
    
    @Override
    public boolean validate() {
        if (config == null) return false;
        
        String template = getConfigValue("template", null);
        if (template == null || template.isEmpty()) {
            log.error("Template node {}: template is required", id);
            return false;
        }
        
        return true;
    }
    
    @Override
    public Map<String, ParameterDef> getInputParameters() {
        Map<String, ParameterDef> params = new HashMap<>();
        
        String template = getConfigValue("template", "");
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
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
        
        String outputKey = getConfigValue("outputKey", "rendered_template");
        
        ParameterDef resultDef = new ParameterDef();
        resultDef.setName(outputKey);
        resultDef.setType("string");
        resultDef.setDescription("Rendered template");
        resultDef.setRequired(true);
        params.put(outputKey, resultDef);
        
        return params;
    }
}
