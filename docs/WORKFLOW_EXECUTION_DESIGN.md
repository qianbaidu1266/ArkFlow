# Workflow 执行与节点快照技术设计方案

## 1. 概述

本文档描述 LangGraph4J 工作流执行系统的技术设计方案，参考 Dify Workflow 的测试运行功能，实现右侧弹出式执行面板，包含输入、结果、详情、追踪四个 Tab 页。

## 2. 功能需求

### 2.1 执行面板布局
- 点击"测试运行"按钮后，右侧滑出执行面板
- 面板宽度：480px，占满编辑器高度
- 面板标题显示："Test Run (HH:mm:ss)" + 关闭按钮

### 2.2 Tab 页设计
1. **输入 (Input)**
   - 显示开始节点的输入变量表单
   - 支持文本输入、文件上传等
   - "开始运行"按钮

2. **结果 (Result)**
   - 显示最终输出结果
   - 支持 JSON 格式化展示
   - 复制结果功能

3. **详情 (Details)**
   - 执行状态（成功/失败）
   - 运行时间、Token 消耗统计
   - 错误信息展示（如有）
   - 输入/输出完整数据

4. **追踪 (Trace)**
   - 节点执行时间线
   - 每个节点的执行状态（成功/失败/运行中）
   - 节点执行耗时
   - 点击节点可查看该节点的输入/输出详情

### 2.3 画布交互
- 执行过程中，节点显示执行状态图标
- 当前执行节点高亮显示
- 已执行完成的节点显示绿色勾号
- 执行失败的节点显示红色错误图标

## 3. 技术架构

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Vue3)                           │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Execution    │  │ Execution    │  │ Canvas Node      │  │
│  │ Panel        │  │ Store        │  │ Status Overlay   │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      后端 (Java/Vert.x)                      │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Execution    │  │ Node         │  │ Snapshot         │  │
│  │ API          │  │ Executor     │  │ Manager          │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      数据存储                                 │
├─────────────────────────────────────────────────────────────┤
│  MySQL (工作流定义)  │  Redis (执行状态)  │  S3/MinIO (快照) │
└─────────────────────────────────────────────────────────────┘
```

## 4. 数据模型设计

### 4.1 执行记录 (Execution)

```java
public class WorkflowExecution {
    private String executionId;           // 执行ID
    private String workflowId;            // 工作流ID
    private String workflowVersion;       // 工作流版本
    private ExecutionStatus status;       // 状态: PENDING, RUNNING, SUCCESS, FAILED, CANCELLED
    private Map<String, Object> inputs;   // 输入参数
    private Map<String, Object> outputs;  // 输出结果
    private String errorMessage;          // 错误信息
    private long startTime;               // 开始时间
    private long endTime;                 // 结束时间
    private long duration;                // 执行耗时(ms)
    private int totalTokens;              // 总Token消耗
    private String triggeredBy;           // 触发者
    private Map<String, Object> metadata; // 元数据
}

public enum ExecutionStatus {
    PENDING,    // 等待执行
    RUNNING,    // 执行中
    SUCCESS,    // 执行成功
    FAILED,     // 执行失败
    CANCELLED   // 已取消
}
```

### 4.2 节点执行快照 (NodeExecutionSnapshot)

```java
public class NodeExecutionSnapshot {
    private String snapshotId;            // 快照ID
    private String executionId;           // 所属执行ID
    private String nodeId;                // 节点ID
    private String nodeType;              // 节点类型
    private String nodeName;              // 节点名称
    
    // 执行状态
    private NodeExecutionStatus status;   // 状态: PENDING, RUNNING, SUCCESS, FAILED, SKIPPED
    private long startTime;               // 开始时间
    private long endTime;                 // 结束时间
    private long duration;                // 执行耗时
    
    // 输入输出数据
    private Map<String, Object> inputs;   // 节点输入
    private Map<String, Object> outputs;  // 节点输出
    private String errorMessage;          // 错误信息
    private String errorStack;            // 错误堆栈
    
    // 性能指标
    private int promptTokens;             // 提示Token数
    private int completionTokens;         // 完成Token数
    private int totalTokens;              // 总Token数
    private String model;                 // 使用的模型
    
    // 存储位置（大数据存储在对象存储）
    private String inputsStorageKey;      // 输入数据存储Key
    private String outputsStorageKey;     // 输出数据存储Key
    private long inputsSize;              // 输入数据大小
    private long outputsSize;             // 输出数据大小
}

public enum NodeExecutionStatus {
    PENDING,    // 等待执行
    RUNNING,    // 执行中
    SUCCESS,    // 执行成功
    FAILED,     // 执行失败
    SKIPPED     // 被跳过（条件分支）
}
```

### 4.3 执行追踪事件 (ExecutionTraceEvent)

```java
public class ExecutionTraceEvent {
    private String eventId;               // 事件ID
    private String executionId;           // 执行ID
    private String nodeId;                // 节点ID
    private TraceEventType eventType;     // 事件类型
    private long timestamp;               // 时间戳
    private Map<String, Object> data;     // 事件数据
}

public enum TraceEventType {
    EXECUTION_STARTED,      // 执行开始
    NODE_STARTED,           // 节点开始
    NODE_COMPLETED,         // 节点完成
    NODE_FAILED,            // 节点失败
    NODE_SKIPPED,           // 节点跳过
    CHECKPOINT_CREATED,     // 检查点创建
    EXECUTION_COMPLETED,    // 执行完成
    EXECUTION_FAILED,       // 执行失败
    STREAM_OUTPUT           // 流式输出
}
```

## 5. 数据库设计

### 5.1 执行记录表

```sql
CREATE TABLE workflow_executions (
    id VARCHAR(64) PRIMARY KEY,
    workflow_id VARCHAR(64) NOT NULL,
    workflow_version INT DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    inputs JSON,
    outputs JSON,
    error_message TEXT,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    duration BIGINT,
    total_tokens INT DEFAULT 0,
    triggered_by VARCHAR(64),
    metadata JSON,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    INDEX idx_workflow_id (workflow_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.2 节点执行快照表

```sql
CREATE TABLE node_execution_snapshots (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    node_type VARCHAR(50) NOT NULL,
    node_name VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    start_time BIGINT,
    end_time BIGINT,
    duration BIGINT,
    inputs JSON,
    outputs JSON,
    error_message TEXT,
    error_stack TEXT,
    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    model VARCHAR(100),
    inputs_storage_key VARCHAR(255),
    outputs_storage_key VARCHAR(255),
    inputs_size BIGINT DEFAULT 0,
    outputs_size BIGINT DEFAULT 0,
    created_at BIGINT NOT NULL,
    INDEX idx_execution_id (execution_id),
    INDEX idx_node_id (node_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.3 执行追踪事件表

```sql
CREATE TABLE execution_trace_events (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64),
    event_type VARCHAR(50) NOT NULL,
    event_data JSON,
    timestamp BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_execution_id (execution_id),
    INDEX idx_event_type (event_type),
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 6. 后端设计

### 6.1 执行服务接口

```java
public interface ExecutionService {
    
    /**
     * 启动工作流执行
     */
    CompletableFuture<ExecutionResult> startExecution(
        String workflowId,
        Map<String, Object> inputs,
        ExecutionConfig config
    );
    
    /**
     * 获取执行状态
     */
    ExecutionStatus getExecutionStatus(String executionId);
    
    /**
     * 获取执行详情
     */
    WorkflowExecution getExecution(String executionId);
    
    /**
     * 获取节点执行快照
     */
    List<NodeExecutionSnapshot> getNodeSnapshots(String executionId);
    
    /**
     * 获取单个节点快照
     */
    NodeExecutionSnapshot getNodeSnapshot(String executionId, String nodeId);
    
    /**
     * 获取执行追踪事件
     */
    List<ExecutionTraceEvent> getTraceEvents(String executionId);
    
    /**
     * 取消执行
     */
    boolean cancelExecution(String executionId);
    
    /**
     * 从检查点恢复执行
     */
    CompletableFuture<ExecutionResult> resumeFromCheckpoint(
        String executionId,
        String nodeId
    );
}
```

### 6.2 节点执行拦截器

```java
public interface NodeExecutionInterceptor {
    
    /**
     * 节点执行前
     */
    void beforeExecute(NodeExecutionContext context);
    
    /**
     * 节点执行后
     */
    void afterExecute(NodeExecutionContext context, NodeExecutionResult result);
    
    /**
     * 节点执行异常
     */
    void onError(NodeExecutionContext context, Throwable error);
}

/**
 * 快照记录拦截器实现
 */
public class SnapshotRecordingInterceptor implements NodeExecutionInterceptor {
    
    private final SnapshotManager snapshotManager;
    
    @Override
    public void beforeExecute(NodeExecutionContext context) {
        // 记录节点开始执行
        NodeExecutionSnapshot snapshot = new NodeExecutionSnapshot();
        snapshot.setExecutionId(context.getExecutionId());
        snapshot.setNodeId(context.getNode().getId());
        snapshot.setNodeType(context.getNode().getType().name());
        snapshot.setNodeName(context.getNode().getName());
        snapshot.setStatus(NodeExecutionStatus.RUNNING);
        snapshot.setStartTime(System.currentTimeMillis());
        snapshot.setInputs(context.getInputs().getAll());
        
        snapshotManager.saveSnapshot(snapshot);
        
        // 发布事件
        publishEvent(TraceEventType.NODE_STARTED, context);
    }
    
    @Override
    public void afterExecute(NodeExecutionContext context, NodeExecutionResult result) {
        // 更新节点执行结果
        NodeExecutionSnapshot snapshot = snapshotManager.getSnapshot(
            context.getExecutionId(), 
            context.getNode().getId()
        );
        
        snapshot.setStatus(NodeExecutionStatus.SUCCESS);
        snapshot.setEndTime(System.currentTimeMillis());
        snapshot.setDuration(snapshot.getEndTime() - snapshot.getStartTime());
        snapshot.setOutputs(result.getOutputs().getAll());
        
        // 记录 Token 消耗
        if (result.getTokenUsage() != null) {
            snapshot.setPromptTokens(result.getTokenUsage().getPromptTokens());
            snapshot.setCompletionTokens(result.getTokenUsage().getCompletionTokens());
            snapshot.setTotalTokens(result.getTokenUsage().getTotalTokens());
        }
        
        snapshotManager.updateSnapshot(snapshot);
        
        // 发布事件
        publishEvent(TraceEventType.NODE_COMPLETED, context, result);
    }
    
    @Override
    public void onError(NodeExecutionContext context, Throwable error) {
        // 记录节点执行失败
        NodeExecutionSnapshot snapshot = snapshotManager.getSnapshot(
            context.getExecutionId(),
            context.getNode().getId()
        );
        
        snapshot.setStatus(NodeExecutionStatus.FAILED);
        snapshot.setEndTime(System.currentTimeMillis());
        snapshot.setDuration(snapshot.getEndTime() - snapshot.getStartTime());
        snapshot.setErrorMessage(error.getMessage());
        snapshot.setErrorStack(ExceptionUtils.getStackTrace(error));
        
        snapshotManager.updateSnapshot(snapshot);
        
        // 发布事件
        publishEvent(TraceEventType.NODE_FAILED, context, error);
    }
}
```

### 6.3 快照管理器

```java
public interface SnapshotManager {
    
    /**
     * 保存快照
     */
    void saveSnapshot(NodeExecutionSnapshot snapshot);
    
    /**
     * 更新快照
     */
    void updateSnapshot(NodeExecutionSnapshot snapshot);
    
    /**
     * 获取快照
     */
    NodeExecutionSnapshot getSnapshot(String executionId, String nodeId);
    
    /**
     * 获取执行的所有快照
     */
    List<NodeExecutionSnapshot> getSnapshots(String executionId);
    
    /**
     * 存储大数据（输入/输出）
     */
    String storeLargeData(String executionId, String nodeId, String type, Object data);
    
    /**
     * 读取大数据
     */
    Object loadLargeData(String storageKey);
}

/**
 * 混合存储实现：MySQL + S3/MinIO
 */
public class HybridSnapshotManager implements SnapshotManager {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectStorageClient objectStorage;
    private final long largeDataThreshold = 1024 * 1024; // 1MB
    
    @Override
    public void saveSnapshot(NodeExecutionSnapshot snapshot) {
        // 检查数据大小，大数据存储到对象存储
        if (snapshot.getInputs() != null) {
            String inputsJson = JsonUtils.toJson(snapshot.getInputs());
            if (inputsJson.length() > largeDataThreshold) {
                String key = storeLargeData(
                    snapshot.getExecutionId(),
                    snapshot.getNodeId(),
                    "inputs",
                    snapshot.getInputs()
                );
                snapshot.setInputsStorageKey(key);
                snapshot.setInputsSize(inputsJson.length());
                snapshot.setInputs(null); // 不存储在MySQL
            }
        }
        
        // 保存到MySQL
        String sql = """
            INSERT INTO node_execution_snapshots 
            (id, execution_id, node_id, node_type, node_name, status, start_time, 
             inputs, inputs_storage_key, inputs_size, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            generateId(),
            snapshot.getExecutionId(),
            snapshot.getNodeId(),
            snapshot.getNodeType(),
            snapshot.getNodeName(),
            snapshot.getStatus().name(),
            snapshot.getStartTime(),
            JsonUtils.toJson(snapshot.getInputs()),
            snapshot.getInputsStorageKey(),
            snapshot.getInputsSize(),
            System.currentTimeMillis()
        );
    }
    
    @Override
    public String storeLargeData(String executionId, String nodeId, String type, Object data) {
        String key = String.format("executions/%s/%s/%s.json", executionId, nodeId, type);
        byte[] content = JsonUtils.toJsonBytes(data);
        objectStorage.putObject(key, content, "application/json");
        return key;
    }
    
    @Override
    public Object loadLargeData(String storageKey) {
        byte[] content = objectStorage.getObject(storageKey);
        return JsonUtils.fromJsonBytes(content, Map.class);
    }
}
```

### 6.4 WebSocket 实时推送

```java
public class ExecutionWebSocketHandler {
    
    private final Map<String, Set<ServerWebSocket>> executionSubscribers = new ConcurrentHashMap<>();
    
    /**
     * 客户端订阅执行事件
     */
    public void subscribe(String executionId, ServerWebSocket socket) {
        executionSubscribers.computeIfAbsent(executionId, k -> ConcurrentHashMap.newKeySet())
            .add(socket);
    }
    
    /**
     * 推送执行事件
     */
    public void pushEvent(String executionId, ExecutionEvent event) {
        Set<ServerWebSocket> sockets = executionSubscribers.get(executionId);
        if (sockets != null) {
            String message = JsonUtils.toJson(event);
            sockets.forEach(socket -> {
                if (!socket.isClosed()) {
                    socket.writeTextMessage(message);
                }
            });
        }
    }
    
    /**
     * 推送节点状态更新
     */
    public void pushNodeStatus(String executionId, String nodeId, NodeExecutionStatus status) {
        ExecutionEvent event = new ExecutionEvent();
        event.setType("NODE_STATUS_UPDATE");
        event.setData(Map.of(
            "nodeId", nodeId,
            "status", status.name(),
            "timestamp", System.currentTimeMillis()
        ));
        pushEvent(executionId, event);
    }
}
```

## 7. 前端设计

### 7.1 组件结构

```
src/components/execution/
├── ExecutionPanel.vue          # 执行面板容器
├── ExecutionTabs.vue           # Tab 切换组件
├── InputTab.vue                # 输入 Tab
├── ResultTab.vue               # 结果 Tab
├── DetailsTab.vue              # 详情 Tab
├── TraceTab.vue                # 追踪 Tab
├── NodeTraceItem.vue           # 节点追踪项
├── NodeStatusBadge.vue         # 节点状态徽章
└── ExecutionTimeline.vue       # 执行时间线
```

### 7.2 ExecutionPanel 组件

```vue
<template>
  <Transition name="slide">
    <div v-if="visible" class="execution-panel">
      <!-- 面板头部 -->
      <div class="panel-header">
        <div class="panel-title">
          <span>Test Run</span>
          <span class="timestamp">({{ formatTime(executionStartTime) }})</span>
        </div>
        <button class="btn-close" @click="close">×</button>
      </div>
      
      <!-- Tab 导航 -->
      <ExecutionTabs 
        v-model:activeTab="activeTab"
        :tabs="tabs"
        :executionStatus="executionStatus"
      />
      
      <!-- Tab 内容 -->
      <div class="tab-content">
        <InputTab
          v-if="activeTab === 'input'"
          :variables="inputVariables"
          :loading="isExecuting"
          @run="startExecution"
        />
        
        <ResultTab
          v-else-if="activeTab === 'result'"
          :result="executionResult"
          :loading="isExecuting"
        />
        
        <DetailsTab
          v-else-if="activeTab === 'details'"
          :execution="executionDetail"
        />
        
        <TraceTab
          v-else-if="activeTab === 'trace'"
          :snapshots="nodeSnapshots"
          :events="traceEvents"
          @selectNode="onSelectNode"
        />
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useExecutionStore } from '@/stores/execution'

const props = defineProps<{
  visible: boolean
  workflowId: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const executionStore = useExecutionStore()
const activeTab = ref('input')
const executionStartTime = ref<number | null>(null)

const tabs = [
  { key: 'input', label: '输入', icon: '⌨' },
  { key: 'result', label: '结果', icon: '📄' },
  { key: 'details', label: '详情', icon: '📊' },
  { key: 'trace', label: '追踪', icon: '🔍' }
]

const isExecuting = computed(() => executionStore.isExecuting)
const executionStatus = computed(() => executionStore.executionStatus)
const executionResult = computed(() => executionStore.result)
const executionDetail = computed(() => executionStore.executionDetail)
const nodeSnapshots = computed(() => executionStore.nodeSnapshots)
const traceEvents = computed(() => executionStore.traceEvents)

async function startExecution(inputs: Record<string, any>) {
  executionStartTime.value = Date.now()
  activeTab.value = 'trace' // 自动切换到追踪页
  
  await executionStore.startExecution(props.workflowId, inputs)
  
  // 执行完成后自动切换到结果页
  if (executionStore.executionStatus === 'SUCCESS') {
    activeTab.value = 'result'
  } else if (executionStore.executionStatus === 'FAILED') {
    activeTab.value = 'details'
  }
}

function close() {
  emit('close')
}
</script>

<style scoped>
.execution-panel {
  position: fixed;
  top: 56px; /* header height */
  right: 0;
  width: 480px;
  height: calc(100vh - 56px);
  background: white;
  border-left: 1px solid #e2e8f0;
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  z-index: 100;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}
</style>
```

### 7.3 TraceTab 组件

```vue
<template>
  <div class="trace-tab">
    <!-- 执行时间线 -->
    <div class="timeline">
      <NodeTraceItem
        v-for="snapshot in snapshots"
        :key="snapshot.nodeId"
        :snapshot="snapshot"
        :selected="selectedNodeId === snapshot.nodeId"
        @click="selectNode(snapshot.nodeId)"
      />
    </div>
    
    <!-- 节点详情弹窗 -->
    <NodeDetailModal
      v-if="selectedNode"
      :snapshot="selectedNode"
      @close="selectedNodeId = null"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { NodeExecutionSnapshot } from '@/types/execution'

const props = defineProps<{
  snapshots: NodeExecutionSnapshot[]
}>()

const selectedNodeId = ref<string | null>(null)

const selectedNode = computed(() => {
  return props.snapshots.find(s => s.nodeId === selectedNodeId.value)
})

function selectNode(nodeId: string) {
  selectedNodeId.value = nodeId
}
</script>
```

### 7.4 Execution Store

```typescript
// stores/execution.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { executionApi } from '@/services/executionApi'
import type { 
  WorkflowExecution, 
  NodeExecutionSnapshot, 
  ExecutionTraceEvent 
} from '@/types/execution'

export const useExecutionStore = defineStore('execution', () => {
  // State
  const currentExecutionId = ref<string | null>(null)
  const executionStatus = ref<string>('IDLE')
  const isExecuting = ref(false)
  const result = ref<any>(null)
  const executionDetail = ref<WorkflowExecution | null>(null)
  const nodeSnapshots = ref<NodeExecutionSnapshot[]>([])
  const traceEvents = ref<ExecutionTraceEvent[]>([])
  
  // WebSocket 连接
  let wsConnection: WebSocket | null = null
  
  // Actions
  async function startExecution(workflowId: string, inputs: Record<string, any>) {
    isExecuting.value = true
    executionStatus.value = 'RUNNING'
    nodeSnapshots.value = []
    traceEvents.value = []
    
    try {
      // 启动执行
      const response = await executionApi.start(workflowId, inputs)
      currentExecutionId.value = response.executionId
      
      // 连接 WebSocket 接收实时更新
      connectWebSocket(response.executionId)
      
      // 轮询等待执行完成
      await pollExecutionStatus(response.executionId)
      
    } catch (error) {
      executionStatus.value = 'FAILED'
      throw error
    } finally {
      isExecuting.value = false
    }
  }
  
  function connectWebSocket(executionId: string) {
    const wsUrl = `ws://localhost:8080/ws/executions/${executionId}`
    wsConnection = new WebSocket(wsUrl)
    
    wsConnection.onmessage = (event) => {
      const message = JSON.parse(event.data)
      handleWebSocketMessage(message)
    }
  }
  
  function handleWebSocketMessage(message: any) {
    switch (message.type) {
      case 'NODE_STATUS_UPDATE':
        updateNodeStatus(message.data.nodeId, message.data.status)
        break
      case 'NODE_COMPLETED':
        loadNodeSnapshot(message.data.nodeId)
        break
      case 'EXECUTION_COMPLETED':
        executionStatus.value = 'SUCCESS'
        result.value = message.data.outputs
        break
      case 'EXECUTION_FAILED':
        executionStatus.value = 'FAILED'
        break
    }
  }
  
  async function pollExecutionStatus(executionId: string) {
    while (isExecuting.value) {
      await new Promise(resolve => setTimeout(resolve, 500))
      
      const status = await executionApi.getStatus(executionId)
      
      if (status === 'SUCCESS' || status === 'FAILED' || status === 'CANCELLED') {
        executionStatus.value = status
        
        // 加载完整数据
        const detail = await executionApi.getDetail(executionId)
        executionDetail.value = detail
        
        const snapshots = await executionApi.getNodeSnapshots(executionId)
        nodeSnapshots.value = snapshots
        
        break
      }
    }
  }
  
  async function loadNodeSnapshot(nodeId: string) {
    if (!currentExecutionId.value) return
    
    const snapshot = await executionApi.getNodeSnapshot(
      currentExecutionId.value, 
      nodeId
    )
    
    const index = nodeSnapshots.value.findIndex(s => s.nodeId === nodeId)
    if (index >= 0) {
      nodeSnapshots.value[index] = snapshot
    } else {
      nodeSnapshots.value.push(snapshot)
    }
  }
  
  function updateNodeStatus(nodeId: string, status: string) {
    const snapshot = nodeSnapshots.value.find(s => s.nodeId === nodeId)
    if (snapshot) {
      snapshot.status = status
    }
  }
  
  return {
    currentExecutionId,
    executionStatus,
    isExecuting,
    result,
    executionDetail,
    nodeSnapshots,
    traceEvents,
    startExecution
  }
})
```

## 8. API 设计

### 8.1 REST API

```yaml
# 执行管理
POST   /api/workflows/{id}/executions      # 启动执行
GET    /api/executions/{id}                # 获取执行详情
GET    /api/executions/{id}/status         # 获取执行状态
DELETE /api/executions/{id}                # 取消执行

# 节点快照
GET    /api/executions/{id}/snapshots      # 获取所有节点快照
GET    /api/executions/{id}/snapshots/{nodeId}  # 获取单个节点快照
GET    /api/executions/{id}/snapshots/{nodeId}/inputs   # 获取节点输入
GET    /api/executions/{id}/snapshots/{nodeId}/outputs  # 获取节点输出

# 追踪事件
GET    /api/executions/{id}/trace          # 获取追踪事件
GET    /api/executions/{id}/trace/stream   # 流式获取追踪事件

# WebSocket
WS     /ws/executions/{id}                 # 实时执行事件
```

### 8.2 WebSocket 消息格式

```typescript
// 节点状态更新
{
  type: 'NODE_STATUS_UPDATE',
  data: {
    nodeId: string,
    status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED',
    timestamp: number
  }
}

// 节点完成
{
  type: 'NODE_COMPLETED',
  data: {
    nodeId: string,
    duration: number,
    outputs: Record<string, any>,
    tokenUsage: {
      promptTokens: number,
      completionTokens: number,
      totalTokens: number
    }
  }
}

// 执行完成
{
  type: 'EXECUTION_COMPLETED',
  data: {
    executionId: string,
    duration: number,
    outputs: Record<string, any>,
    totalTokens: number
  }
}

// 执行失败
{
  type: 'EXECUTION_FAILED',
  data: {
    executionId: string,
    error: string,
    failedNodeId: string
  }
}

// 流式输出（用于 LLM 节点）
{
  type: 'STREAM_OUTPUT',
  data: {
    nodeId: string,
    chunk: string,
    isComplete: boolean
  }
}
```

## 9. 画布节点状态可视化

### 9.1 节点状态样式

```typescript
// 节点状态样式配置
const nodeStatusStyles = {
  PENDING: {
    borderColor: '#e2e8f0',
    backgroundColor: '#f8fafc',
    icon: null
  },
  RUNNING: {
    borderColor: '#3b82f6',
    backgroundColor: '#eff6ff',
    icon: '⏳', // 或动画 spinner
    pulse: true
  },
  SUCCESS: {
    borderColor: '#10b981',
    backgroundColor: '#d1fae5',
    icon: '✓'
  },
  FAILED: {
    borderColor: '#ef4444',
    backgroundColor: '#fee2e2',
    icon: '✗'
  },
  SKIPPED: {
    borderColor: '#94a3b8',
    backgroundColor: '#f1f5f9',
    icon: '⊘',
    opacity: 0.6
  }
}
```

### 9.2 WorkflowNode 组件更新

```vue
<template>
  <div 
    class="workflow-node"
    :class="[
      `node-type-${type}`,
      { selected, executing: isExecuting, 'execution-failed': isFailed }
    ]"
    :style="nodeStyle"
  >
    <!-- 执行状态徽章 -->
    <NodeStatusBadge 
      v-if="executionStatus"
      :status="executionStatus"
      :duration="executionDuration"
    />
    
    <!-- 节点内容 -->
    <div class="node-content">
      <span class="node-icon">{{ icon }}</span>
      <span class="node-name">{{ name }}</span>
    </div>
    
    <!-- 执行进度条 -->
    <div v-if="isExecuting" class="execution-progress">
      <div class="progress-bar" :style="{ width: progress + '%' }"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useExecutionStore } from '@/stores/execution'

const props = defineProps<{
  nodeId: string
  type: string
  name: string
  // ...
}>()

const executionStore = useExecutionStore()

const nodeSnapshot = computed(() => {
  return executionStore.nodeSnapshots.find(s => s.nodeId === props.nodeId)
})

const executionStatus = computed(() => nodeSnapshot.value?.status)
const isExecuting = computed(() => executionStatus.value === 'RUNNING')
const isFailed = computed(() => executionStatus.value === 'FAILED')
const executionDuration = computed(() => nodeSnapshot.value?.duration)

const nodeStyle = computed(() => {
  const style = getNodeBaseStyle(props.type)
  
  if (executionStatus.value) {
    const statusStyle = nodeStatusStyles[executionStatus.value]
    style.borderColor = statusStyle.borderColor
    style.backgroundColor = statusStyle.backgroundColor
    
    if (statusStyle.opacity) {
      style.opacity = statusStyle.opacity
    }
  }
  
  return style
})
</script>
```

## 10. 性能优化

### 10.1 大数据处理
- 输入/输出数据超过 1MB 时，存储到对象存储（S3/MinIO）
- MySQL 只存储元数据和存储 Key
- 前端按需加载节点详情数据

### 10.2 分页和懒加载
- 追踪事件支持分页查询
- 节点快照按需加载
- 大图/文件支持预览和下载

### 10.3 缓存策略
- Redis 缓存执行状态
- 节点快照缓存热点数据
- 前端状态管理缓存

## 11. 安全考虑

### 11.1 数据隔离
- 执行数据按工作流隔离
- 敏感数据（API Key）不存储在快照中
- 支持数据加密存储

### 11.2 权限控制
- 只有工作流所有者可以查看执行记录
- 支持分享执行链接（带过期时间）
- 执行数据定期清理

## 12. 实现计划

### Phase 1: 基础功能
1. 数据库表设计实现
2. 后端执行快照记录
3. 前端执行面板框架
4. 输入/结果 Tab 实现

### Phase 2: 追踪功能
1. 节点快照存储优化
2. 追踪 Tab 实现
3. WebSocket 实时推送
4. 画布节点状态可视化

### Phase 3: 高级功能
1. 详情 Tab 完整信息
2. 执行历史列表
3. 执行对比功能
4. 性能分析图表

---

**文档版本**: 1.0  
**创建日期**: 2026-03-17  
**作者**: AI Assistant
