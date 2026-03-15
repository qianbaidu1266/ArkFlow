package com.langgraph4j.engine.core;

/**
 * 边类型枚举
 */
public enum EdgeType {
    NORMAL("normal", "普通边"),
    CONDITIONAL("conditional", "条件边"),
    PARALLEL("parallel", "并行边"),
    LOOP("loop", "循环边");
    
    private final String code;
    private final String name;
    
    EdgeType(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
}
