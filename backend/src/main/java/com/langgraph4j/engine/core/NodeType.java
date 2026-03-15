package com.langgraph4j.engine.core;

/**
 * 节点类型枚举
 */
public enum NodeType {
    // 开始节点
    START("start", "开始", "工作流入口节点"),
    
    // 结束节点
    END("end", "结束", "工作流出口节点"),
    
    // LLM节点
    LLM("llm", "LLM", "大语言模型调用节点"),
    
    // Agent节点
    AGENT("agent", "Agent", "智能体节点"),
    
    // 条件分支节点
    CONDITION("condition", "条件分支", "条件判断节点"),
    
    // 知识检索节点
    KNOWLEDGE_RETRIEVAL("knowledge_retrieval", "知识检索", "从知识库检索相关内容"),
    
    // 代码执行节点
    CODE("code", "代码执行", "执行自定义代码"),
    
    // HTTP请求节点
    HTTP("http", "HTTP请求", "发送HTTP请求"),
    
    // 变量赋值节点
    VARIABLE_ASSIGNER("variable_assigner", "变量赋值", "设置变量值"),
    
    // 迭代节点
    ITERATION("iteration", "迭代", "循环执行子工作流"),
    
    // 并行节点
    PARALLEL("parallel", "并行", "并行执行多个分支"),
    
    // 聚合节点
    AGGREGATE("aggregate", "聚合", "合并多个分支结果"),
    
    // 工具调用节点
    TOOL("tool", "工具调用", "调用外部工具"),
    
    // 模板节点
    TEMPLATE("template", "模板", "文本模板渲染"),
    
    // 问答节点
    QUESTION_CLASSIFIER("question_classifier", "问题分类", "问题类型分类"),
    
    // 重排节点
    RERANK("rerank", "重排", "对检索结果重排序");
    
    private final String code;
    private final String name;
    private final String description;
    
    NodeType(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static NodeType fromCode(String code) {
        for (NodeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + code);
    }
}
