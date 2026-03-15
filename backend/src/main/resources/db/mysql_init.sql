-- LangGraph4J MySQL 数据库初始化脚本
-- 用于存储工作流配置（节点、连线等）

-- 工作流表
CREATE TABLE IF NOT EXISTS workflows (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    entry_point VARCHAR(64),
    status VARCHAR(20) DEFAULT 'draft',
    version INT DEFAULT 1,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    created_by VARCHAR(64),
    metadata JSON,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义表';

-- 工作流节点表
CREATE TABLE IF NOT EXISTS workflow_nodes (
    id VARCHAR(64) PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    config JSON,
    position_x DOUBLE DEFAULT 0,
    position_y DOUBLE DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_workflow_id (workflow_id),
    CONSTRAINT fk_workflow_nodes_workflow FOREIGN KEY (workflow_id) 
        REFERENCES workflows(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流节点表';

-- 工作流边表
CREATE TABLE IF NOT EXISTS workflow_edges (
    id VARCHAR(64) PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    from_node VARCHAR(64) NOT NULL,
    to_node VARCHAR(64),
    type VARCHAR(20) DEFAULT 'normal',
    conditions JSON,
    default_target VARCHAR(64),
    priority INT DEFAULT 0,
    created_at BIGINT NOT NULL,
    INDEX idx_workflow_id (workflow_id),
    CONSTRAINT fk_workflow_edges_workflow FOREIGN KEY (workflow_id) 
        REFERENCES workflows(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流连线表';

-- 执行记录表
CREATE TABLE IF NOT EXISTS workflow_executions (
    id VARCHAR(64) PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    input JSON,
    output JSON,
    error TEXT,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    duration BIGINT,
    created_at BIGINT NOT NULL,
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_status (status),
    CONSTRAINT fk_executions_workflow FOREIGN KEY (workflow_id) 
        REFERENCES workflows(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流执行记录表';
