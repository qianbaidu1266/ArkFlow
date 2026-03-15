# LangGraph4J API 文档

## 基础信息

- **Base URL**: `http://localhost:8080/api`
- **Content-Type**: `application/json`

## 工作流管理

### 获取工作流列表

```http
GET /workflows
```

**响应**:
```json
{
  "workflows": [
    {
      "id": "workflow_001",
      "name": "RAG Workflow",
      "description": "知识库问答工作流",
      "createdAt": 1705315200000,
      "updatedAt": 1705315200000
    }
  ]
}
```

### 获取工作流详情

```http
GET /workflows/:id
```

**响应**:
```json
{
  "id": "workflow_001",
  "name": "RAG Workflow",
  "entryPoint": "start_1",
  "nodes": {
    "start_1": {
      "id": "start_1",
      "name": "Start",
      "type": "start",
      "config": {},
      "position": { "x": 100, "y": 300 }
    }
  },
  "edges": []
}
```

### 创建工作流

```http
POST /workflows
```

**请求体**:
```json
{
  "id": "workflow_001",
  "name": "RAG Workflow",
  "entryPoint": "start_1",
  "nodes": {},
  "edges": []
}
```

**响应**:
```json
{
  "id": "workflow_001",
  "status": "created"
}
```

### 更新工作流

```http
PUT /workflows/:id
```

**请求体**: 同创建工作流

**响应**:
```json
{
  "id": "workflow_001",
  "status": "updated"
}
```

### 删除工作流

```http
DELETE /workflows/:id
```

**响应**: `204 No Content`

## 工作流执行

### 执行工作流

```http
POST /workflows/:id/execute
```

**请求体**:
```json
{
  "inputs": {
    "query": "什么是人工智能？"
  },
  "config": {
    "timeout": 30000,
    "enableCheckpoint": true
  }
}
```

**响应**:
```json
{
  "executionId": "exec_abc123",
  "success": true,
  "duration": 2500,
  "output": {
    "response": "人工智能是..."
  }
}
```

### 获取执行结果

```http
GET /executions/:id
```

**响应**:
```json
{
  "executionId": "exec_abc123",
  "workflowId": "workflow_001",
  "success": true,
  "output": {},
  "duration": 2500,
  "startTime": 1705315200000,
  "endTime": 1705315202500
}
```

### 获取检查点

```http
GET /executions/:id/checkpoints
```

**响应**:
```json
{
  "checkpoints": [
    {
      "executionId": "exec_abc123",
      "nodeId": "llm_1",
      "state": {},
      "timestamp": 1705315201000,
      "sequence": 1
    }
  ]
}
```

## 节点类型

### 获取支持的节点类型

```http
GET /node-types
```

**响应**:
```json
[
  {
    "code": "start",
    "name": "开始",
    "description": "工作流入口节点"
  },
  {
    "code": "llm",
    "name": "LLM",
    "description": "大语言模型调用节点"
  }
]
```

## 节点配置参考

### LLM 节点

```json
{
  "type": "llm",
  "config": {
    "systemPrompt": "系统提示词，可使用 {{variable}} 引用变量",
    "userPrompt": "用户提示词，可使用 {{variable}} 引用变量",
    "model": "gpt-3.5-turbo",
    "temperature": 0.7,
    "maxTokens": 2000,
    "outputKey": "llm_output"
  }
}
```

### Agent 节点

```json
{
  "type": "agent",
  "config": {
    "systemPrompt": "系统提示词",
    "userPrompt": "用户提示词",
    "maxIterations": 5,
    "tools": [
      {
        "name": "search",
        "description": "搜索工具"
      }
    ],
    "outputKey": "agent_output"
  }
}
```

### 条件分支节点

```json
{
  "type": "condition",
  "config": {
    "conditionType": "expression",
    "expression": "variable > 10",
    "inputVariable": "status"
  }
}
```

### 知识检索节点

```json
{
  "type": "knowledge_retrieval",
  "config": {
    "knowledgeBaseId": "default_kb",
    "queryVariable": "query",
    "topK": 5,
    "scoreThreshold": 0.7,
    "searchType": "similarity",
    "outputKey": "retrieved_context"
  }
}
```

### HTTP 节点

```json
{
  "type": "http",
  "config": {
    "url": "https://api.example.com/data",
    "method": "POST",
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "{\"key\": \"value\"}",
    "timeout": 30000,
    "outputKey": "http_response"
  }
}
```

### 代码执行节点

```json
{
  "type": "code",
  "config": {
    "language": "javascript",
    "code": "const result = input * 2; result;",
    "inputMappings": {
      "input": "source_variable"
    },
    "outputKey": "code_result"
  }
}
```

### 模板节点

```json
{
  "type": "template",
  "config": {
    "template": "Hello, {{name}}!",
    "outputFormat": "text",
    "outputKey": "rendered_template"
  }
}
```

## 错误处理

### 错误响应格式

```json
{
  "error": "错误信息",
  "code": "ERROR_CODE",
  "details": {}
}
```

### 常见错误码

| 状态码 | 说明 |
|-------|------|
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

## WebSocket (未来支持)

### 实时执行日志

```
WS /ws/executions/:id/logs
```

### 实时节点状态

```
WS /ws/executions/:id/nodes
```
