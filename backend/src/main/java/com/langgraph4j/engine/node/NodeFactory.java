package com.langgraph4j.engine.node;

import com.langgraph4j.engine.core.Node;
import com.langgraph4j.engine.core.NodeType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 节点工厂
 * 用于创建各种类型的节点实例
 */
@Slf4j
public class NodeFactory {
    
    /**
     * 创建节点
     */
    public static Node createNode(String id, String name, NodeType type) {
        log.debug("Creating node: {} ({})", id, type);
        
        switch (type) {
            case START:
                return new StartNode(id, name);
            case END:
                return new EndNode(id, name);
            case LLM:
                return new LLMNode(id, name);
            case AGENT:
                return new AgentNode(id, name);
            case CONDITION:
                return new ConditionNode(id, name);
            case KNOWLEDGE_RETRIEVAL:
                return new KnowledgeRetrievalNode(id, name);
            case CODE:
                return new CodeNode(id, name);
            case HTTP:
                return new HttpNode(id, name);
            case TEMPLATE:
                return new TemplateNode(id, name);
            case VARIABLE_ASSIGNER:
                return new VariableAssignerNode(id, name);
            default:
                throw new IllegalArgumentException("Unsupported node type: " + type);
        }
    }
    
    /**
     * 创建节点（带配置）
     */
    public static Node createNode(String id, String name, NodeType type, Map<String, Object> config) {
        Node node = createNode(id, name, type);
        if (config != null) {
            node.setConfig(config);
        }
        return node;
    }
    
    /**
     * 从字符串类型创建节点
     */
    public static Node createNode(String id, String name, String typeCode) {
        NodeType type = NodeType.fromCode(typeCode);
        return createNode(id, name, type);
    }
    
    /**
     * 从字符串类型创建节点（带配置）
     */
    public static Node createNode(String id, String name, String typeCode, Map<String, Object> config) {
        NodeType type = NodeType.fromCode(typeCode);
        return createNode(id, name, type, config);
    }
}
