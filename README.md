# LangGraph4J - 可视化 AI 工作流引擎

基于 LangGraph4J 开发的可视化 AI 工作流引擎，对标 Dify 的节点编排能力。

## 核心特性

### 后端 (Java)
- **流程编排引擎**: 基于图结构的流程编排，支持并行执行
- **多节点类型**: LLM、Agent、条件分支、知识检索、代码执行、HTTP请求等
- **自定义模型接入**: 支持 LLM/Embedding 模型的自定义接入
- **多数据库支持**: MySQL、Redis、PGVector
- **RAG 知识库**: 完整的知识库管理和检索功能
- **分布式状态管理**: Redis 检查点机制
- **完全 Java 实现**: 不依赖 Spring AI

### 前端 (Vue)
- **自由拖拽画布**: 支持节点的自由拖拽和连接
- **可视化编辑器**: 直观的节点配置界面
- **实时预览**: 工作流执行结果实时展示

## 技术栈

### 后端
- Java 17
- Vert.x (异步框架)
- Jackson (JSON处理)
- HikariCP (连接池)
- Lettuce (Redis客户端)
- PGVector (向量数据库)

### 前端
- Vue 3 + TypeScript
- Pinia (状态管理)
- Vue Router
- Tailwind CSS
- Dagre (自动布局)

## 快速开始

### 环境要求
- Java 17+
- Node.js 18+
- Redis 6+
- PostgreSQL 14+ (with pgvector extension)

### 后端启动

```bash
cd backend
mvn clean package
java -jar target/langgraph4j-engine-1.0.0-SNAPSHOT.jar
```

或使用 Docker:

```bash
docker-compose up -d
```

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

### 环境变量

```bash
# LLM 配置
export OPENAI_API_KEY=your_api_key
export LLM_MODEL=gpt-3.5-turbo

# Redis 配置
export REDIS_URI=redis://localhost:6379

# PGVector 配置
export PGVECTOR_URL=jdbc:postgresql://localhost:5432/langgraph4j
export PGVECTOR_USER=postgres
export PGVECTOR_PASSWORD=your_password

# 服务端口
export SERVER_PORT=8080
```

## 节点类型

| 节点类型 | 说明 |
|---------|------|
| Start | 工作流入口节点 |
| End | 工作流出口节点 |
| LLM | 大语言模型调用 |
| Agent | 智能体节点，支持工具调用 |
| Condition | 条件分支节点 |
| Knowledge Retrieval | 知识库检索 |
| Code | 代码执行节点 |
| HTTP | HTTP请求节点 |
| Template | 模板渲染节点 |
| Variable Assigner | 变量赋值节点 |

## API 文档

### 工作流管理

```
GET    /api/workflows          # 获取工作流列表
GET    /api/workflows/:id      # 获取工作流详情
POST   /api/workflows          # 创建工作流
PUT    /api/workflows/:id      # 更新工作流
DELETE /api/workflows/:id      # 删除工作流
```

### 工作流执行

```
POST   /api/workflows/:id/execute  # 执行工作流
GET    /api/executions/:id         # 获取执行结果
GET    /api/executions/:id/checkpoints  # 获取检查点
```

### 节点类型

```
GET    /api/node-types         # 获取支持的节点类型
```

## 工作流定义格式

```json
{
  "id": "workflow_001",
  "name": "RAG Workflow",
  "entryPoint": "start_001",
  "nodes": {
    "start_001": {
      "id": "start_001",
      "name": "Start",
      "type": "start",
      "config": {},
      "position": { "x": 100, "y": 300 }
    },
    "llm_001": {
      "id": "llm_001",
      "name": "LLM",
      "type": "llm",
      "config": {
        "systemPrompt": "You are a helpful assistant.",
        "userPrompt": "{{query}}",
        "model": "gpt-3.5-turbo",
        "temperature": 0.7,
        "outputKey": "response"
      },
      "position": { "x": 400, "y": 300 }
    }
  },
  "edges": [
    { "id": "edge_001", "from": "start_001", "to": "llm_001", "type": "normal" }
  ]
}
```

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Node Palette │  │    Canvas    │  │  Properties  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Backend (Java/Vert.x)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  WorkflowApi │  │WorkflowEngine│  │     Graph    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   LLMNode    │  │  AgentNode   │  │KnowledgeNode │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│    Redis     │      │  PostgreSQL  │      │  LLM APIs    │
│  (Checkpoint)│      │  (PGVector)  │      │(OpenAI/etc.) │
└──────────────┘      └──────────────┘      └──────────────┘
```

## 贡献

欢迎提交 Issue 和 Pull Request!

## 许可证

MIT License
