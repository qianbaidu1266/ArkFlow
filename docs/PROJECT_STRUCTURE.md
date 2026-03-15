# LangGraph4J 项目结构

```
langgraph4j-engine/
├── README.md                    # 项目说明文档
├── PROJECT_STRUCTURE.md         # 项目结构文档
├── docker-compose.yml           # Docker Compose 配置
├── build.sh                     # 构建脚本
│
├── backend/                     # Java 后端
│   ├── Dockerfile               # 后端 Docker 配置
│   ├── pom.xml                  # Maven 配置
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── langgraph4j/
│       │   │           └── engine/
│       │   │               ├── LangGraph4JApplication.java    # 应用入口
│       │   │               │
│       │   │               ├── api/                           # REST API
│       │   │               │   └── WorkflowApi.java           # 工作流 API
│       │   │               │
│       │   │               ├── core/                          # 核心引擎
│       │   │               │   ├── Graph.java                 # 图结构
│       │   │               │   ├── Node.java                  # 节点基类
│       │   │               │   ├── Edge.java                  # 边定义
│       │   │               │   ├── NodeType.java              # 节点类型枚举
│       │   │               │   ├── EdgeType.java              # 边类型枚举
│       │   │               │   ├── Condition.java             # 条件定义
│       │   │               │   ├── ExecutionContext.java      # 执行上下文
│       │   │               │   └── WorkflowEngine.java        # 工作流引擎
│       │   │               │
│       │   │               ├── node/                          # 节点实现
│       │   │               │   ├── NodeFactory.java           # 节点工厂
│       │   │               │   ├── StartNode.java             # 开始节点
│       │   │               │   ├── EndNode.java               # 结束节点
│       │   │               │   ├── LLMNode.java               # LLM节点
│       │   │               │   ├── AgentNode.java             # Agent节点
│       │   │               │   ├── ConditionNode.java         # 条件节点
│       │   │               │   ├── KnowledgeRetrievalNode.java # 知识检索节点
│       │   │               │   ├── CodeNode.java              # 代码节点
│       │   │               │   ├── HttpNode.java              # HTTP节点
│       │   │               │   ├── TemplateNode.java          # 模板节点
│       │   │               │   └── VariableAssignerNode.java  # 变量赋值节点
│       │   │               │
│       │   │               ├── model/                         # 模型接入
│       │   │               │   ├── LLMClient.java             # LLM客户端
│       │   │               │   └── EmbeddingClient.java       # Embedding客户端
│       │   │               │
│       │   │               ├── rag/                           # RAG知识库
│       │   │               │   ├── KnowledgeBase.java         # 知识库基类
│       │   │               │   └── PGVectorKnowledgeBase.java # PGVector实现
│       │   │               │
│       │   │               └── state/                         # 状态管理
│       │   │                   ├── GraphState.java            # 图状态
│       │   │                   ├── CheckpointManager.java     # 检查点接口
│       │   │                   └── RedisCheckpointManager.java # Redis实现
│       │   │
│       │   └── resources/
│       │       ├── application.properties    # 应用配置
│       │       └── logback.xml               # 日志配置
│       │
│       └── test/                             # 测试代码
│
└── frontend/                    # Vue 前端
    ├── Dockerfile               # 前端 Docker 配置
    ├── nginx.conf               # Nginx 配置
    ├── package.json             # NPM 配置
    ├── vite.config.ts           # Vite 配置
    ├── tsconfig.json            # TypeScript 配置
    ├── tailwind.config.js       # Tailwind 配置
    ├── index.html               # 入口 HTML
    │
    └── src/
        ├── main.ts              # 应用入口
        ├── App.vue              # 根组件
        │
        ├── router/              # 路由配置
        │   └── index.ts
        │
        ├── stores/              # Pinia 状态管理
        │   ├── workflow.ts      # 工作流状态
        │   └── canvas.ts        # 画布状态
        │
        ├── services/            # API 服务
        │   └── api.ts
        │
        ├── types/               # TypeScript 类型
        │   └── workflow.ts
        │
        ├── config/              # 配置文件
        │   └── nodeTypes.ts     # 节点类型配置
        │
        ├── views/               # 页面视图
        │   ├── Home.vue         # 首页
        │   ├── Editor.vue       # 编辑器
        │   └── Workflows.vue    # 工作流列表
        │
        ├── components/          # 组件
        │   ├── EditorToolbar.vue      # 编辑器工具栏
        │   ├── NodePalette.vue        # 节点面板
        │   ├── WorkflowCanvas.vue     # 工作流画布
        │   ├── WorkflowNode.vue       # 工作流节点
        │   ├── ConnectionLine.vue     # 连接线
        │   ├── PropertyPanel.vue      # 属性面板
        │   └── ExecutionPanel.vue     # 执行面板
        │
        └── styles/              # 样式文件
            └── index.scss
```

## 核心模块说明

### 1. 流程编排引擎 (core)

- **Graph**: 有向图结构，支持并行执行和条件分支
- **Node**: 节点基类，所有节点类型的父类
- **Edge**: 边定义，支持普通边和条件边
- **WorkflowEngine**: 工作流引擎，管理和执行工作流
- **ExecutionContext**: 执行上下文，包含所有依赖资源

### 2. 节点类型 (node)

| 节点 | 说明 |
|-----|------|
| StartNode | 工作流入口，定义输入变量 |
| EndNode | 工作流出口，定义输出变量 |
| LLMNode | 调用大语言模型 |
| AgentNode | 智能体，支持工具调用 |
| ConditionNode | 条件分支，支持表达式和多分支 |
| KnowledgeRetrievalNode | 知识库检索 |
| CodeNode | 执行 JavaScript 代码 |
| HttpNode | 发送 HTTP 请求 |
| TemplateNode | 模板渲染 |
| VariableAssignerNode | 变量赋值和转换 |

### 3. 模型接入 (model)

- **LLMClient**: 支持 OpenAI 等 LLM 提供商
- **EmbeddingClient**: 支持文本向量化

### 4. RAG 知识库 (rag)

- **KnowledgeBase**: 知识库抽象基类
- **PGVectorKnowledgeBase**: 基于 PostgreSQL + pgvector 的实现

### 5. 状态管理 (state)

- **GraphState**: 图执行状态，支持嵌套结构
- **CheckpointManager**: 检查点接口
- **RedisCheckpointManager**: Redis 实现，支持分布式

## 前端架构

### 状态管理

- **workflow store**: 管理工作流数据（节点、边、配置）
- **canvas store**: 管理画布状态（缩放、偏移、选择）

### 核心组件

- **WorkflowCanvas**: 画布组件，处理拖拽、缩放、连接
- **WorkflowNode**: 节点组件，显示节点信息和连接点
- **ConnectionLine**: 连接线组件，渲染贝塞尔曲线
- **PropertyPanel**: 属性面板，配置节点参数
- **NodePalette**: 节点面板，拖拽添加节点

## 数据流

```
用户操作 → Canvas Store → Workflow Store → API → Backend
                ↓
         画布重绘 ← 状态更新
```

## 扩展点

### 添加新节点类型

1. 在 `backend/src/main/java/com/langgraph4j/engine/node/` 创建新节点类
2. 在 `NodeType.java` 添加节点类型枚举
3. 在 `NodeFactory.java` 注册节点创建逻辑
4. 在 `frontend/src/config/nodeTypes.ts` 添加前端配置

### 添加新模型提供商

1. 实现 `LLMClient` 或 `EmbeddingClient` 的子类
2. 在 `LangGraph4JApplication.java` 配置新的客户端
