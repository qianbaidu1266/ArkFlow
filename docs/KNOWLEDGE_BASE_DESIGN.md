# 知识库功能设计方案

> 日期: 2026-06-07
> 参考: Dify RAG 知识系统架构

## 1. 我们要构建什么

一个多知识库管理系统，支持用户创建多个独立知识库（如「产品文档库」「FAQ 库」），每个知识库可上传文档文件，系统自动完成文本提取、分块、向量化，并支持 Embedding + BM25 多路召回和 Reranker 重排序。工作流中的知识检索节点可绑定指定知识库进行检索。

## 2. 为什么选择这个方案

### 考虑过的方案

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| **A: MySQL + Milvus 混合存储（推荐）** | MySQL 存元数据和文档信息，Milvus 存向量索引和分块内容 | 职责清晰，MySQL 擅长关系查询，Milvus 擅长向量检索 | 两个存储需保持一致性 |
| B: 纯 Milvus 存储 | 所有数据存 Milvus 标量字段 | 架构简单 | Milvus 不擅长关系查询，大文本性能差，无法做复杂筛选 |
| C: 纯 MySQL + PGVector | 不用 Milvus | 最少依赖 | 不支持 BM25 稀疏向量，检索能力受限 |

**选择方案 A**：MySQL 负责知识库/文档的 CRUD 和元数据管理，Milvus 负责向量索引和相似性检索。两者通过 `chunk_id` 和 `document_id` 关联。

## 3. 核心数据模型

### 3.1 MySQL 表结构

```
┌─────────────────────────┐       ┌──────────────────────────────┐       ┌──────────────────────────────┐
│      knowledge_bases     │       │         documents            │       │       document_chunks         │
├─────────────────────────┤       ├──────────────────────────────┤       ├──────────────────────────────┤
│ id (PK)                 │──1:N──│ id (PK)                      │──1:N──│ id (PK)                      │
│ name                    │       │ knowledge_base_id (FK)       │       │ document_id (FK)             │
│ description             │       │ title                        │       │ content                      │
│ embedding_model         │       │ source_file_name             │       │ chunk_index                  │
│ embedding_dimensions    │       │ file_type                    │       │ token_count                  │
│ search_type             │       │ file_size                    │       │ word_count                   │
│ enable_bm25             │       │ word_count                   │       │ milvus_chunk_id              │
│ enable_reranker         │       │ indexing_status              │       │ created_at                   │
│ chunk_size              │       │ chunk_count                  │       │ updated_at                   │
│ chunk_overlap           │       │ error_message                │       └──────────────────────────────┘
│ score_threshold         │       │ created_at                   │
│ top_k                   │       │ updated_at                   │
│ created_at              │       └──────────────────────────────┘
│ updated_at              │
└─────────────────────────┘
```

**knowledge_bases** - 知识库配置

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) PK | 知识库 ID |
| name | VARCHAR(255) | 知识库名称 |
| description | TEXT | 描述 |
| embedding_model | VARCHAR(128) | Embedding 模型名称 |
| embedding_dimensions | INT | 向量维度 |
| search_type | VARCHAR(32) | 默认检索方式: similarity / bm25 / hybrid |
| enable_bm25 | BOOLEAN | 是否启用 BM25 |
| enable_reranker | BOOLEAN | 是否启用 Reranker |
| chunk_size | INT | 分块大小（字符数） |
| chunk_overlap | INT | 分块重叠 |
| score_threshold | FLOAT | 分数阈值 |
| top_k | INT | 默认返回条数 |
| created_at | BIGINT | 创建时间 |
| updated_at | BIGINT | 更新时间 |

**documents** - 文档元数据

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) PK | 文档 ID |
| knowledge_base_id | VARCHAR(64) FK | 所属知识库 |
| title | VARCHAR(512) | 文档标题 |
| source_file_name | VARCHAR(512) | 原始文件名 |
| file_type | VARCHAR(32) | 文件类型: pdf/txt/md/docx |
| file_size | BIGINT | 文件大小(字节) |
| word_count | INT | 字数 |
| indexing_status | VARCHAR(32) | 索引状态: pending/indexing/completed/error |
| chunk_count | INT | 分块数量 |
| error_message | TEXT | 错误信息 |
| created_at | BIGINT | 创建时间 |
| updated_at | BIGINT | 更新时间 |

**document_chunks** - 文档分块

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) PK | 分块 ID |
| document_id | VARCHAR(64) FK | 所属文档 |
| content | TEXT | 分块文本内容 |
| chunk_index | INT | 分块序号 |
| token_count | INT | Token 数量 |
| word_count | INT | 字数 |
| milvus_chunk_id | VARCHAR(128) | Milvus 中对应的 chunk_id |
| created_at | BIGINT | 创建时间 |
| updated_at | BIGINT | 更新时间 |

### 3.2 Milvus Collection 结构

每个知识库对应一个 Milvus Collection，命名规则: `kb_{knowledge_base_id}`。

| 字段 | 类型 | 说明 |
|------|------|------|
| chunk_id | VarChar(128) PK | 分块 ID |
| document_id | VarChar(128) | 文档 ID（用于按文档删除） |
| content | VarChar(65535) | 分块内容（用于搜索结果返回） |
| chunk_index | Int32 | 分块序号 |
| embedding | FloatVector(dims) | Dense 向量（Embedding） |
| sparse_embedding | SparseFloatVector | Sparse 向量（BM25，可选） |

**索引配置**:
- `embedding`: HNSW (M=16, efConstruction=256, metric=COSINE)
- `sparse_embedding`: SPARSE_INVERTED_INDEX (metric=IP, drop_ratio_build=0.2)

## 4. 文档处理 Pipeline

参考 Dify 的 ETL 模式，文档处理分为三个阶段：

```
上传文件 → [提取] → [转换] → [加载] → 索引完成
              │         │         │
              ▼         ▼         ▼
          文本提取    分块+清洗   向量化+存储
         (PDF/TXT/   (按配置     (Embedding
          MD/DOCX)   参数分块)   + BM25)
```

### 4.1 提取阶段 (Extract)

| 文件类型 | 提取方式 | 依赖 |
|---------|---------|------|
| .txt | 直接读取 | JDK 内置 |
| .md | 直接读取 | JDK 内置 |
| .pdf | Apache PDFBox | pdfbox 依赖 |
| .docx | Apache POI | poi 依赖 |

### 4.2 转换阶段 (Transform)

1. **文本清洗**: 去除多余空白、特殊字符
2. **分块**: 按知识库配置的 chunk_size 和 chunk_overlap 分块
3. **分块元数据**: 记录每个分块的 token_count、word_count

### 4.3 加载阶段 (Load)

1. 将分块元数据写入 MySQL `document_chunks` 表
2. 调用 Embedding 模型批量生成向量
3. 如果启用 BM25，生成稀疏向量
4. 将向量数据写入 Milvus Collection
5. 更新文档状态为 `completed`

### 4.4 异步处理

文档索引是耗时操作，采用异步处理：

```
上传文件 → 创建文档记录(status=pending) → 返回文档ID
                                              │
                                     异步线程执行 ETL
                                              │
                              ┌────────────────┴────────────────┐
                              │                                  │
                        成功: status=completed            失败: status=error
                        chunk_count=N                    error_message=...
```

前端通过轮询 `GET /api/knowledge-bases/{id}/documents/{docId}` 获取索引进度。

## 5. 检索架构

### 5.1 两级检索配置（参考 Dify）

```
┌─────────────────────────────────────────────────┐
│              知识库级别配置（默认值）              │
│  search_type: hybrid                            │
│  enable_bm25: true                              │
│  enable_reranker: true                          │
│  top_k: 5, score_threshold: 0.7                 │
├─────────────────────────────────────────────────┤
│              节点级别配置（可覆盖）               │
│  search_type: similarity (覆盖)                 │
│  top_k: 10 (覆盖)                               │
│  score_threshold: 0.5 (覆盖)                    │
└─────────────────────────────────────────────────┘
```

- **知识库级别**: 创建知识库时设置的默认检索参数
- **节点级别**: 工作流中知识检索节点可覆盖知识库默认值

### 5.2 检索流程

```
用户查询
    │
    ▼
┌──────────────┐     ┌──────────────┐
│ Dense 检索    │     │ Sparse 检索  │
│ (Embedding)  │     │ (BM25)       │
│ topK * 2     │     │ topK * 2     │
└──────┬───────┘     └──────┬───────┘
       │                    │
       └────────┬───────────┘
                ▼
        RRF 融合排序
                │
                ▼
        Reranker 重排序（可选）
                │
                ▼
        分数过滤 (threshold)
                │
                ▼
          返回 topK 结果
```

### 5.3 检索模式说明

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| similarity | 纯 Embedding 向量检索 | 语义相似性查询 |
| bm25 | 纯 BM25 关键词检索 | 精确关键词匹配 |
| hybrid | Dense + Sparse 并行检索 + RRF 融合 | 通用场景（推荐） |

## 6. API 设计

### 6.1 知识库管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge-bases` | 列出所有知识库 |
| POST | `/api/knowledge-bases` | 创建知识库 |
| GET | `/api/knowledge-bases/{id}` | 获取知识库详情 |
| PUT | `/api/knowledge-bases/{id}` | 更新知识库配置 |
| DELETE | `/api/knowledge-bases/{id}` | 删除知识库（含所有文档和向量） |

### 6.2 文档管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge-bases/{id}/documents` | 列出知识库下的文档 |
| POST | `/api/knowledge-bases/{id}/documents/upload` | 上传文档文件 |
| GET | `/api/knowledge-bases/{id}/documents/{docId}` | 获取文档详情（含索引状态） |
| DELETE | `/api/knowledge-bases/{id}/documents/{docId}` | 删除文档（含分块和向量） |
| POST | `/api/knowledge-bases/{id}/documents/{docId}/reindex` | 重新索引文档 |

### 6.3 分块管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge-bases/{id}/documents/{docId}/chunks` | 列出文档的分块 |
| PUT | `/api/knowledge-bases/{id}/documents/{docId}/chunks/{chunkId}` | 编辑分块内容 |
| DELETE | `/api/knowledge-bases/{id}/documents/{docId}/chunks/{chunkId}` | 删除分块 |
| POST | `/api/knowledge-bases/{id}/documents/{docId}/chunks` | 手动添加分块 |

### 6.4 检索测试

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge-bases/{id}/search` | 检索测试（用于调试） |

请求示例:
```json
{
  "query": "如何配置数据库连接",
  "searchType": "hybrid",
  "topK": 5,
  "scoreThreshold": 0.5
}
```

响应示例:
```json
{
  "query": "如何配置数据库连接",
  "results": [
    {
      "chunkId": "xxx",
      "documentId": "yyy",
      "documentTitle": "部署指南",
      "content": "数据库连接配置需要在 application.properties 中...",
      "chunkIndex": 3,
      "score": 0.92,
      "searchType": "hybrid"
    }
  ],
  "total": 5,
  "searchType": "hybrid"
}
```

### 6.5 系统配置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/config/vector-db/status` | 获取向量数据库连接状态 |

## 7. 前端页面设计

### 7.1 知识库列表页 (`/knowledge`)

```
┌──────────────────────────────────────────────────────────┐
│  知识库                                    [+ 新建知识库] │
│  管理 RAG 知识库，为工作流提供检索能力                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────┐ │
│  │ 📚 产品文档库   │  │ 📚 FAQ 库      │  │ 📚 API文档  │ │
│  │ 12 个文档       │  │ 5 个文档       │  │ 3 个文档    │ │
│  │ 1,234 个分块    │  │ 456 个分块     │  │ 789 个分块  │ │
│  │ hybrid 检索     │  │ similarity     │  │ hybrid     │ │
│  │ BM25 ✓ Reranker│  │ BM25 ✗        │  │ BM25 ✓     │ │
│  │ 2 分钟前更新    │  │ 1 小时前       │  │ 昨天        │ │
│  └────────────────┘  └────────────────┘  └────────────┘ │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 7.2 新建知识库弹窗

```
┌─────────────────────────────────────────┐
│  新建知识库                         ✕    │
├─────────────────────────────────────────┤
│                                         │
│  名称 *     [                    ]      │
│  描述       [                    ]      │
│                                         │
│  ── 检索配置 ──                          │
│                                         │
│  检索方式   [hybrid ▾]                   │
│             ○ similarity 语义检索        │
│             ○ bm25 关键词检索            │
│             ● hybrid 混合检索（推荐）    │
│                                         │
│  启用 BM25  [✓]                         │
│  启用 Reranker [ ]                      │
│                                         │
│  ── 分块配置 ──                          │
│                                         │
│  分块大小   [1000] 字符                  │
│  重叠大小   [200]  字符                  │
│  分数阈值   [0.7]                        │
│  返回条数   [5]                          │
│                                         │
│              [取消]  [创建]              │
└─────────────────────────────────────────┘
```

### 7.3 知识库详情页 (`/knowledge/:id`)

```
┌──────────────────────────────────────────────────────────┐
│  ← 返回    产品文档库                    [设置] [删除]   │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐ │
│  │ 12 文档   │ │ 1,234块  │ │ hybrid   │ │ BM25+Rerank│ │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘ │
│                                                          │
│  ── 文档列表 ──────────────────── [+ 上传文档] ──────── │
│                                                          │
│  📄 部署指南.pdf        328块  ✅ 已完成  [删除] [重建] │
│  📄 API参考.md          156块  ✅ 已完成  [删除] [重建] │
│  📄 常见问题.txt         45块  🔄 索引中  [删除]        │
│  📄 架构设计.pdf        210块  ❌ 失败    [删除] [重试] │
│                                                          │
│  ── 检索测试 ────────────────────────────────────────── │
│                                                          │
│  [输入查询问题...                           ] [检索]     │
│                                                          │
│  检索结果:                                               │
│  1. [0.92] 部署指南.pdf#3 - 数据库连接配置需要在...     │
│  2. [0.87] API参考.md#12 - 连接池参数说明...            │
│  3. [0.81] 架构设计.pdf#7 - 微服务间通信...             │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

## 8. 后端代码结构

### 8.1 新增/修改文件

```
backend/src/main/java/com/langgraph4j/engine/
├── api/
│   └── KnowledgeApi.java              ← 新增: 知识库 REST API
├── config/
│   ├── MilvusConfig.java              ✅ 已创建
│   └── RerankerConfig.java            ✅ 已创建
├── di/
│   ├── ConfigModule.java              ✅ 已更新
│   └── LLMModule.java                 ✅ 已更新
├── rag/
│   ├── KnowledgeBase.java             ← 修改: 适配多知识库
│   ├── MilvusKnowledgeBase.java       ✅ 已创建
│   ├── RerankerClient.java            ✅ 已创建
│   ├── Bm25Util.java                  ✅ 已创建
│   ├── DocumentExtractor.java         ← 新增: 文档文本提取
│   └── KnowledgeBaseManager.java      ← 新增: 多知识库管理器
└── repository/
    └── KnowledgeBaseRepository.java   ← 新增: MySQL 知识库数据访问
```

### 8.2 关键类职责

**KnowledgeBaseManager** - 多知识库管理器
- 维护 `Map<String, KnowledgeBase>` 知识库实例缓存
- 根据知识库 ID 获取或创建 KnowledgeBase 实例
- 删除知识库时释放 Milvus Collection 资源

**KnowledgeBaseRepository** - MySQL 数据访问
- 知识库/文档/分块的 CRUD 操作
- 文档索引状态更新

**DocumentExtractor** - 文档文本提取
- 支持 PDF/TXT/MD/DOCX 格式
- 统一输出纯文本

**KnowledgeApi** - REST API
- 知识库管理接口
- 文档上传和索引
- 检索测试

### 8.3 与现有架构的集成

```
                    ┌─────────────────┐
                    │  WorkflowApi     │
                    │  (现有)          │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌──────────────┐
     │ Workflow   │  │ Knowledge  │  │ Knowledge    │
     │ Engine     │  │ Api (新增) │  │ RetrievalNode│
     │ (现有)     │  │            │  │ (现有)        │
     └────────────┘  └─────┬──────┘  └──────┬───────┘
                           │                │
                           ▼                ▼
                  ┌─────────────────────────────┐
                  │    KnowledgeBaseManager      │
                  │    (新增: 多知识库管理)       │
                  └──────────┬──────────────────┘
                             │
                    ┌────────┴────────┐
                    ▼                 ▼
           ┌──────────────┐  ┌──────────────────┐
           │ MySQL        │  │ Milvus            │
           │ (元数据)     │  │ (向量索引)        │
           └──────────────┘  └──────────────────┘
```

**KnowledgeRetrievalNode 改造**:
- 当前通过 `context.getKnowledgeBase()` 获取全局唯一实例
- 改为通过 `context.getKnowledgeBaseManager()` 获取管理器
- 根据节点配置的 `knowledgeBaseId` 获取对应知识库实例

## 9. 关键决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 多知识库 vs 单知识库 | 多知识库 | 用户需要按业务隔离，工作流可绑定不同知识库 |
| 元数据存储 | MySQL + Milvus | MySQL 擅长关系查询，Milvus 擅长向量检索 |
| 文档索引方式 | 异步处理 | 索引是耗时操作，不能阻塞 API 请求 |
| 分块内容存储 | MySQL + Milvus 双写 | MySQL 存完整内容用于管理，Milvus 存用于搜索结果返回 |
| 文件上传方式 | multipart/form-data | 标准文件上传方式，Vert.x 原生支持 |
| 原始文件存储 | 本地文件系统 `data/uploads/{kb_id}/{doc_id}.{ext}` | 保留原始文件方便重新索引，后续可扩展到 S3/OSS |
| 文件大小限制 | 50MB | 平衡实用性和服务器压力 |
| 文档重新上传 | 覆盖 | 同名文件重新上传时覆盖旧文档及其分块和向量 |
| 分块手动编辑 | 支持 | 用户可编辑自动分块的结果，修改后重新向量化 |
| Milvus 部署 | Docker 本地部署 | 开发和生产均使用 Docker，最简方式 |

### 9.1 数据存储分工

```
┌─────────────────────────────────────────────────────────────┐
│                       数据存储分工                           │
├──────────────┬──────────────────────────────────────────────┤
│ 本地文件系统  │ 原始文件 (PDF/TXT/MD/DOCX)                  │
│              │ 路径: data/uploads/{kb_id}/{doc_id}.{ext}    │
├──────────────┼──────────────────────────────────────────────┤
│ MySQL        │ 知识库配置 (name, search_type, chunk_size…)  │
│              │ 文档元数据 (title, file_type, status…)       │
│              │ 分块内容 (content, chunk_index, token_count…) │
├──────────────┼──────────────────────────────────────────────┤
│ Milvus       │ Dense 向量 (Embedding, FloatVector)          │
│              │ Sparse 向量 (BM25, SparseFloatVector)        │
│              │ 分块内容副本 (content, 用于搜索结果返回)      │
│              │ 关联字段 (chunk_id, document_id)              │
└──────────────┴──────────────────────────────────────────────┘
```

### 9.2 Milvus 部署方式

**Docker 部署（推荐）**：
```bash
# Standalone 模式，适合开发和中小规模生产
docker run -d --name milvus \
  -p 19530:19530 \
  -p 9091:9091 \
  -v $(pwd)/milvus-data:/var/lib/milvus \
  milvusdb/milvus:v2.4-latest
```

**Milvus Lite（开发测试）**：
- 嵌入式模式，Java 进程内运行
- 无需 Docker，零运维
- 数据量 < 100 万条时性能足够
- 适合本地开发和 CI 环境

**Zilliz Cloud（生产环境）**：
- 托管 Milvus 服务，免运维
- 自动扩缩容，高可用
- 按用量付费

### 9.3 分块手动编辑

用户可对自动分块结果进行编辑：

```
┌──────────────────────────────────────────────────────────┐
│  文档: 部署指南.pdf                                      │
│  ── 分块列表 ─────────────────────── [保存修改] ──────── │
│                                                          │
│  分块 #1 [编辑] [删除]                                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │ 数据库连接配置需要在 application.properties 中...   │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  分块 #2 [编辑] [删除]                                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Redis 缓存配置默认使用 localhost:6379...            │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  [+ 添加分块]                                            │
└──────────────────────────────────────────────────────────┘
```

编辑操作：
- **修改内容**: 修改分块文本，保存后重新向量化并更新 Milvus
- **删除分块**: 从 MySQL 和 Milvus 中同时删除
- **添加分块**: 手动添加新分块，自动向量化并存入 Milvus
- **合并分块**: 将相邻分块合并为一个（后续迭代）

### 9.4 文档覆盖逻辑

重新上传同名文件时：
1. 删除旧文档在 Milvus 中的所有向量（按 document_id 批量删除）
2. 删除旧文档在 MySQL 中的分块记录
3. 删除本地文件系统中的旧文件
4. 创建新文档记录（status=pending）
5. 执行 ETL Pipeline 重新索引

## 10. 开放问题

- [x] 文件上传大小限制 → 50MB
- [x] 文档重新上传 → 覆盖旧文档
- [x] Milvus Collection 策略 → 单 Collection + Partition Key
- [x] content 存储 → 双写 Milvus + MySQL（参考 Dify）
- [x] 分块手动编辑 → 第一版就做，见 9.3 分块手动编辑
- [ ] Milvus Collection 的数据一致性如何保证？→ 已分析，见 11.3 问题 5
- [ ] 是否需要知识库的权限控制？→ 单用户系统暂不需要

## 11. 设计评审 & 风险分析

### 11.1 评审总结

共发现 **9 个问题**，按风险等级分类：

| 风险 | 问题 | 状态 |
|------|------|------|
| 高 | Collection 数量爆炸 | ✅ 已解决，改为单 Collection + Partition Key |
| 高 | MySQL + Milvus 双写一致性 | ⚠️ 有方案，见 11.3 问题 5 |
| 中 | content 双写 | ✅ 已确认保留，参考 Dify |
| 中 | embedding_model 多模型冲突 | ✅ 第一版统一全局模型 |
| 中 | 缺少 source_file_path | ✅ 已追加字段 |
| 中 | 分块编辑一致性 | ⚠️ 有方案，见 11.3 问题 6 |
| 中 | KnowledgeBaseManager 缓存失效 | ⚠️ 需实现时处理 |
| 中 | Milvus 连接失败降级 | ⚠️ 需实现时处理 |
| 低 | 异步线程池未定义 | ⚠️ 需实现时配置 |

### 11.2 架构问题

#### 问题 1: Collection 数量爆炸（已解决）

~~当前设计"每个知识库一个 Milvus Collection"~~ → **已确认采用单 Collection + Partition Key 方案**

#### 问题 2: content 双写（已确认保留）

参考 Dify 设计，分块内容在 Milvus 和 MySQL 中都存储。理由：
- 检索时直接从 Milvus 返回 content，无需回查 MySQL，减少一次网络往返
- Dify 同样采用此策略，搜索结果直接包含完整内容
- 不一致风险通过"先 Milvus 后 MySQL"的写入顺序 + 定期巡检来缓解

#### 问题 3: embedding_model 知识库级别配置的冲突（中风险）

当前设计知识库级别配置 `embedding_model`，但系统只有一个全局 `EmbeddingClient`：

```java
// LLMModule 中全局注入
EmbeddingClient embeddingClient = new OpenAiEmbeddingClient(...)
```

如果知识库 A 用 `bge-large-zh`（1024维），知识库 B 用 `m3e-base`（768维），无法切换。

**建议修改**: 第一版统一使用全局 Embedding 模型，`embedding_model` 字段仅作为记录，不做运行时切换。多模型支持作为后续迭代。

#### 问题 4: 缺少原始文件路径记录（中风险）

`documents` 表缺少 `source_file_path` 字段，删除知识库时无法清理本地文件。

**建议修改**: 增加 `source_file_path VARCHAR(1024)` 字段。

### 11.3 数据一致性风险

#### 问题 5: MySQL + Milvus 双写无事务保障（高风险）

ETL Pipeline 步骤 4 写入 Milvus 时，如果失败：
- MySQL 中分块记录已存在（status=completed）
- Milvus 中没有对应向量
- 检索时这些分块永远不会被召回，但元数据显示索引成功

**建议修改**: 采用"先 Milvus 后 MySQL"的顺序 + 最终一致性：

```
1. 写入 Milvus (向量)
2. 写入 MySQL (分块元数据, status=completed)
3. 如果步骤 2 失败 → 删除步骤 1 写入的 Milvus 数据
```

配合定期巡检：扫描 MySQL 中 `status=completed` 但 Milvus 中无对应向量的分块，标记为 `error` 并触发重新索引。

#### 问题 6: 分块编辑后物理删除 vs 逻辑删除（中风险）

分块编辑 → 重新向量化 → 更新 Milvus。Milvus 的更新操作本质是 delete + insert，存在短暂窗口期该分块不可检索。

**建议修改**: 分块编辑操作拆分为：
1. 先插入新向量到 Milvus（新 chunk_id）
2. 更新 MySQL 分块记录
3. 删除 Milvus 中旧向量

这样保证编辑过程中分块始终可检索。

### 11.4 运行时风险

#### 问题 7: KnowledgeBaseManager 缓存失效（中风险）

```java
Map<String, KnowledgeBase> cache = new ConcurrentHashMap<>();
```

用户修改知识库配置（如 chunk_size、search_type）后，缓存的实例不会更新。需要重启才能生效。

**建议修改**: 
- 配置修改时主动 invalidate 缓存
- 或者 KnowledgeBase 实例不缓存配置，每次检索时从 MySQL 读取最新配置

#### 问题 8: 异步处理线程池未定义（低风险）

大量文档同时上传，异步线程池可能耗尽。

**建议修改**: 明确线程池配置，建议使用有界队列：

```java
ExecutorService indexExecutor = new ThreadPoolExecutor(
    2, 4, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行
);
```

#### 问题 9: Milvus 连接失败时无降级策略（中风险）

如果 Milvus 不可用，整个知识库系统不可用，但没有明确错误处理和用户提示。

**建议修改**:
- 启动时检测 Milvus 连接，不可用时知识库功能显示"向量数据库不可用"
- 检索时 Milvus 异常 → 返回明确错误而非空结果
- 文档上传时 Milvus 异常 → 文档标记为 error，不静默失败

### 11.5 缺失功能

| 缺失项 | 影响 | 建议 |
|--------|------|------|
| 文档列表分页 | 文档多时接口响应慢 | 添加 `?page=1&size=20` 分页参数 |
| 批量删除文档 | 只能逐个操作 | 添加 `POST /documents/batch-delete` 接口 |
| 检索结果高亮 | 用户体验差 | 返回匹配片段位置，前端高亮 |
| 知识库导入导出 | 无法迁移 | 后续迭代 |
| 文档类型支持 .doc | 旧格式 Word 文档 | 后续迭代，优先 PDF/TXT/MD/DOCX |

### 11.6 简化建议

以下功能建议第一版不做，降低复杂度：

| 功能 | 原因 |
|------|------|
| 分块的 token_count/word_count | 统计开销大，实际价值有限 |
| 知识库级别的 embedding_model 切换 | 全局统一模型已够用 |

### 11.7 确认后的最终数据模型

**MySQL documents 表变更**:
- 新增 `source_file_path VARCHAR(1024)` - 本地文件路径

**Milvus Collection 结构**（单 Collection + Partition Key）:

```
Collection: kb_chunks（全局唯一 Collection）
├── chunk_id (VarChar PK)
├── knowledge_base_id (VarChar, Partition Key)  ← 按知识库分区
├── document_id (VarChar)
├── content (VarChar 65535)                     ← 保留，检索时直接返回
├── embedding (FloatVector)
└── sparse_embedding (SparseFloatVector)
```

**索引配置**:
- `embedding`: HNSW (M=16, efConstruction=256, metric=COSINE)
- `sparse_embedding`: SPARSE_INVERTED_INDEX (metric=IP, drop_ratio_build=0.2)

**与 Dify 对比**:

| 维度 | Dify | 本方案 |
|------|------|--------|
| 向量数据库 | 支持多种（Weaviate/Qdrant/Milvus等） | 仅 Milvus |
| 元数据存储 | PostgreSQL | MySQL |
| 检索方式 | 关键词 + 向量 + 混合 | Similarity + BM25 + Hybrid |
| 重排序 | 支持 Rerank 模型 | 支持 Reranker |
| Collection 策略 | 每个知识库独立 Collection | 单 Collection + Partition Key |
| 多模型 Embedding | 支持 | 第一版不支持，统一全局模型 |

## 12. 实施步骤

### Phase 1: 基础框架
1. 创建 MySQL 表结构 (knowledge_bases, documents, document_chunks)
2. 实现 KnowledgeBaseRepository (MySQL CRUD)
3. 实现 KnowledgeBaseManager (多知识库实例管理)
4. 实现 KnowledgeApi (知识库 CRUD + 文档上传)

### Phase 2: 文档处理
5. 实现 DocumentExtractor (TXT/MD/PDF/DOCX 提取)
6. 实现异步文档索引 Pipeline (提取 → 分块 → 向量化 → 存储)
7. 文档状态管理和前端轮询

### Phase 3: 前端页面
8. 知识库列表页 + 新建弹窗
9. 知识库详情页（文档列表 + 上传 + 分块编辑 + 检索测试）
10. 设置页面 Milvus/Reranker 状态展示

### Phase 4: 工作流集成
11. 改造 KnowledgeRetrievalNode 支持多知识库选择
12. 工作流编辑器中知识检索节点的知识库选择 UI
