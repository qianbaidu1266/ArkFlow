# LangGraph4J 项目总结

## 项目概述

LangGraph4J 是一个基于 Java 开发的可视化 AI 工作流引擎，对标 Dify 的节点编排能力。项目采用前后端分离架构，提供完整的流程编排、执行和管理功能。

## 代码统计

| 模块 | 文件数 | 代码行数 |
|-----|-------|---------|
| 后端 (Java) | 27 | ~5,180 |
| 前端 (Vue/TS) | 20 | ~3,335 |
| 配置文件 | 15 | ~800 |
| **总计** | **62** | **~9,300** |

## 核心功能实现

### 1. 流程编排引擎 ✅

- **图结构**: 有向图支持，节点和边的灵活连接
- **并行执行**: 支持分支并行执行和结果合并
- **条件分支**: 支持表达式和多分支条件判断
- **循环支持**: 可扩展支持迭代节点

### 2. 节点类型 ✅

| 节点 | 状态 | 说明 |
|-----|------|------|
| Start | ✅ | 工作流入口，支持输入变量定义 |
| End | ✅ | 工作流出口，支持输出变量定义 |
| LLM | ✅ | 大语言模型调用，支持多提供商 |
| Agent | ✅ | 智能体节点，支持工具调用 |
| Condition | ✅ | 条件分支，支持表达式和多分支 |
| Knowledge Retrieval | ✅ | 知识库检索，RAG 支持 |
| Code | ✅ | JavaScript 代码执行 |
| HTTP | ✅ | HTTP 请求，支持各种方法 |
| Template | ✅ | 模板渲染，支持变量替换 |
| Variable Assigner | ✅ | 变量赋值和转换 |

### 3. 模型接入 ✅

- **LLMClient**: 支持 OpenAI 等标准 API 格式
- **EmbeddingClient**: 支持文本向量化
- **自定义配置**: 支持自定义模型参数和端点

### 4. 数据库支持 ✅

- **Redis**: 检查点管理、分布式状态
- **PostgreSQL + PGVector**: 向量存储、知识库
- **连接池**: HikariCP 高性能连接池

### 5. RAG 知识库 ✅

- **文档管理**: 添加、删除、查询文档
- **向量检索**: 相似度搜索、混合搜索
- **分块策略**: 可配置的分块大小和重叠
- **元数据支持**: 文档元数据存储

### 6. 状态管理 ✅

- **GraphState**: 状态容器，支持嵌套结构
- **CheckpointManager**: 检查点接口
- **RedisCheckpointManager**: 分布式检查点实现
- **状态恢复**: 支持从检查点恢复执行

### 7. REST API ✅

- **工作流 CRUD**: 完整的增删改查接口
- **执行接口**: 同步/异步执行工作流
- **检查点接口**: 查询和管理检查点
- **节点类型接口**: 获取支持的节点类型

### 8. 前端编辑器 ✅

- **拖拽画布**: 自由拖拽节点和画布
- **节点连接**: 可视化连接节点
- **属性面板**: 节点配置界面
- **缩放平移**: 画布缩放和平移
- **自动布局**: Dagre 自动布局

## 技术亮点

### 后端

1. **纯 Java 实现**: 不依赖 Spring AI，轻量级
2. **异步框架**: Vert.x 提供高性能异步处理能力
3. **模块化设计**: 清晰的模块划分，易于扩展
4. **类型安全**: 完整的类型定义和验证

### 前端

1. **Vue 3 + TypeScript**: 现代化前端技术栈
2. **Pinia 状态管理**: 清晰的状态管理
3. **Tailwind CSS**: 高效的样式开发
4. **响应式设计**: 适配不同屏幕尺寸

## 项目结构

```
langgraph4j-engine/
├── backend/           # Java 后端
│   ├── core/          # 核心引擎
│   ├── node/          # 节点实现
│   ├── model/         # 模型接入
│   ├── rag/           # 知识库
│   ├── state/         # 状态管理
│   └── api/           # REST API
├── frontend/          # Vue 前端
│   ├── components/    # 组件
│   ├── views/         # 页面
│   ├── stores/        # 状态管理
│   └── config/        # 配置
├── examples/          # 示例工作流
└── docs/              # 文档
```

## 使用示例

### 创建 RAG 工作流

1. 打开编辑器: http://localhost:3000
2. 从左侧拖拽节点到画布:
   - Start → Knowledge Retrieval → Template → LLM → End
3. 连接节点
4. 配置节点参数
5. 点击运行

### API 调用

```bash
# 执行工作流
curl -X POST http://localhost:8080/api/workflows/workflow_001/execute \
  -H "Content-Type: application/json" \
  -d '{
    "inputs": {
      "query": "什么是人工智能？"
    }
  }'
```

## 部署方式

1. **本地开发**: Maven + npm
2. **Docker**: docker-compose up -d
3. **生产环境**: Docker + Nginx + SSL

## 扩展指南

### 添加新节点类型

1. 后端: 继承 `Node` 类，实现 `execute` 方法
2. 前端: 在 `nodeTypes.ts` 添加配置
3. 注册: 在 `NodeFactory` 注册创建逻辑

### 添加新模型提供商

1. 实现 `LLMClient` 或 `EmbeddingClient` 子类
2. 在 `LangGraph4JApplication` 配置

## 未来规划

- [ ] WebSocket 实时日志
- [ ] 工作流版本管理
- [ ] 权限控制
- [ ] 工作流调度
- [ ] 更多节点类型
- [ ] 插件系统

## 贡献指南

欢迎提交 Issue 和 Pull Request!

## 许可证

MIT License
