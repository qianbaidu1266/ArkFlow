package com.langgraph4j.engine.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 条件定义
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Condition {
    
    /**
     * 条件映射: 条件值 -> 目标节点ID
     */
    private Map<String, String> conditions;
    
    /**
     * 默认目标节点ID
     */
    private String defaultTarget;
    
    /**
     * 条件表达式 (用于复杂条件)
     */
    private String expression;
    
    public Condition(Map<String, String> conditions, String defaultTarget) {
        this.conditions = conditions;
        this.defaultTarget = defaultTarget;
    }
    
    /**
     * 评估条件
     */
    public String evaluate(String conditionValue) {
        if (conditions != null && conditionValue != null) {
            String target = conditions.get(conditionValue);
            if (target != null) {
                return target;
            }
        }
        return defaultTarget;
    }
}
