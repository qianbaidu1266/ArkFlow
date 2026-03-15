<template>
  <div class="toolbar">
    <div class="toolbar-left">
      <router-link to="/" class="logo">
        <span class="logo-icon">◈</span>
        <span class="logo-text">LangGraph4J</span>
      </router-link>
      
      <div class="toolbar-divider"></div>
      
      <div class="workflow-name">
        <input 
          v-if="workflowStore.currentWorkflow"
          v-model="workflowStore.currentWorkflow.name"
          type="text"
          class="name-input"
          placeholder="工作流名称"
        />
      </div>
    </div>
    
    <div class="toolbar-center">
      <button class="toolbar-btn" @click="canvasStore.resetView">
        <span class="icon">⌖</span>
        重置视图
      </button>
      <button class="toolbar-btn" @click="autoLayout">
        <span class="icon">⚡</span>
        自动布局
      </button>
    </div>
    
    <div class="toolbar-right">
      <button class="toolbar-btn" @click="saveWorkflow" :disabled="workflowStore.isLoading">
        <span class="icon">💾</span>
        保存
      </button>
      <button class="toolbar-btn primary" @click="runWorkflow" :disabled="workflowStore.isLoading">
        <span class="icon">▶</span>
        运行
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useWorkflowStore } from '@/stores/workflow'
import { useCanvasStore } from '@/stores/canvas'
import dagre from 'dagre'

const workflowStore = useWorkflowStore()
const canvasStore = useCanvasStore()

// 保存工作流
async function saveWorkflow() {
  try {
    await workflowStore.saveWorkflow()
    alert('工作流已保存')
  } catch (e) {
    alert('保存失败: ' + (e as Error).message)
  }
}

// 运行工作流
async function runWorkflow() {
  try {
    const result = await workflowStore.execute()
    console.log('Execution result:', result)
  } catch (e) {
    alert('执行失败: ' + (e as Error).message)
  }
}

// 自动布局
function autoLayout() {
  if (!workflowStore.currentWorkflow) return
  
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'LR', nodesep: 80, ranksep: 120 })
  g.setDefaultEdgeLabel(() => ({}))
  
  // 添加节点
  Object.values(workflowStore.currentWorkflow.nodes).forEach(node => {
    g.setNode(node.id, { width: 180, height: 80 })
  })
  
  // 添加边
  workflowStore.currentWorkflow.edges.forEach(edge => {
    g.setEdge(edge.from, edge.to)
  })
  
  // 计算布局
  dagre.layout(g)
  
  // 更新节点位置
  g.nodes().forEach(nodeId => {
    const node = g.node(nodeId)
    workflowStore.updateNode(nodeId, {
      position: { x: node.x, y: node.y }
    })
  })
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.logo-icon {
  font-size: 20px;
  color: #3b82f6;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.workflow-name {
  .name-input {
    padding: 6px 12px;
    border: 1px solid transparent;
    border-radius: 6px;
    font-size: 14px;
    font-weight: 500;
    color: #1e293b;
    background: transparent;
    min-width: 200px;
    
    &:hover {
      border-color: #e2e8f0;
    }
    
    &:focus {
      outline: none;
      border-color: #3b82f6;
      background: white;
    }
  }
}

.toolbar-center {
  display: flex;
  gap: 8px;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
  background: transparent;
  color: #64748b;
  
  &:hover:not(:disabled) {
    background: #f1f5f9;
    color: #1e293b;
  }
  
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  
  &.primary {
    background: #3b82f6;
    color: white;
    
    &:hover:not(:disabled) {
      background: #2563eb;
    }
  }
  
  .icon {
    margin-right: 6px;
    font-size: 14px;
  }
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background: #e2e8f0;
}
</style>
