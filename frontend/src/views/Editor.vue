<template>
  <div class="editor">
    <!-- 顶部标题栏 -->
    <header class="editor-header">
      <div class="header-left">
        <router-link to="/" class="back-btn">
          ←
        </router-link>
        <div class="workflow-info">
          <input 
            v-if="workflowStore.currentWorkflow"
            v-model="workflowStore.currentWorkflow.name"
            type="text"
            class="workflow-name-input"
            placeholder="工作流名称"
          />
          <span class="workflow-status" v-if="workflowStore.currentWorkflow">
            自动保存于 {{ formatTime(workflowStore.currentWorkflow.updatedAt) }}
          </span>
        </div>
      </div>
      
      <div class="header-right">
        <button class="header-btn" @click="saveWorkflow" :disabled="workflowStore.isLoading">
          保存
        </button>
        <button class="header-btn primary" @click="runWorkflow" :disabled="workflowStore.isLoading">
          运行
        </button>
      </div>
    </header>
    
    <!-- 主体区域 -->
    <div class="editor-body">
      <!-- 中间画布 -->
      <div class="canvas-wrapper" :class="{ 'with-panel': canvasStore.editingNodeId }">
        <WorkflowCanvas />
      </div>
      
      <!-- 右侧属性面板 - 只在编辑节点时显示 -->
      <PropertyPanel v-if="canvasStore.editingNodeId" />
    </div>
    
    <!-- 底部工具栏 - 固定在视口底部居中 -->
    <div class="bottom-toolbar">
      <div class="toolbar-group">
        <button class="toolbar-btn" title="撤销" @click="undo">
          ↶
        </button>
        <button class="toolbar-btn" title="重做" @click="redo">
          ↷
        </button>
      </div>
      
      <div class="toolbar-divider"></div>
      
      <div class="toolbar-group">
        <button class="toolbar-btn" title="缩小" @click="zoomOut">
          −
        </button>
        <span class="zoom-value">{{ Math.round(canvasStore.scale * 100) }}%</span>
        <button class="toolbar-btn" title="放大" @click="zoomIn">
          +
        </button>
        <button class="toolbar-btn" title="适应画布" @click="fitCanvas">
          ⊡
        </button>
      </div>
      
      <div class="toolbar-divider"></div>
      
      <div class="toolbar-group">
        <button class="toolbar-btn" title="自动布局" @click="autoLayout">
          ⚡
        </button>
      </div>
      
      <div class="toolbar-divider"></div>
      
      <div class="add-node-wrapper">
        <button class="add-node-btn" @click.stop="toggleNodeMenu">
          + 添加节点
        </button>
        
        <!-- 添加节点菜单 -->
        <Teleport to="body">
          <div 
            v-if="showNodeMenu" 
            class="node-menu-dropdown"
            :style="menuStyle"
          >
            <div 
              v-for="nodeType in nodeTypeList" 
              :key="nodeType.code"
              class="node-menu-item"
              draggable="true"
              @dragstart="handleDragStart($event, nodeType.code)"
              @dragend="handleDragEnd"
              @click="addNode(nodeType.code)"
            >
              <span class="node-icon" :style="{ background: nodeType.color }">
                {{ nodeType.icon }}
              </span>
              <span class="node-name">{{ nodeType.name }}</span>
            </div>
          </div>
        </Teleport>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useWorkflowStore } from '@/stores/workflow'
import { useCanvasStore } from '@/stores/canvas'
import WorkflowCanvas from '@/components/WorkflowCanvas.vue'
import PropertyPanel from '@/components/PropertyPanel.vue'
import { nodeTypeList } from '@/config/nodeTypes'
import type { NodeType } from '@/types/workflow'
import dagre from 'dagre'

const route = useRoute()
const workflowStore = useWorkflowStore()
const canvasStore = useCanvasStore()

const showNodeMenu = ref(false)
const menuStyle = ref({})
const isDragging = ref(false)

function toggleNodeMenu(event: MouseEvent) {
  showNodeMenu.value = !showNodeMenu.value
  
  if (showNodeMenu.value) {
    nextTick(() => {
      const btn = event.currentTarget as HTMLElement
      const rect = btn.getBoundingClientRect()
      menuStyle.value = {
        position: 'fixed',
        bottom: `${window.innerHeight - rect.top + 8}px`,
        left: `${rect.left + rect.width / 2}px`,
        transform: 'translateX(-50%)'
      }
    })
  }
}

function closeNodeMenu() {
  if (!isDragging.value) {
    showNodeMenu.value = false
  }
}

function handleClickOutside(event: MouseEvent) {
  if (showNodeMenu.value && !isDragging.value) {
    const target = event.target as HTMLElement
    if (!target.closest('.add-node-btn') && !target.closest('.node-menu-dropdown')) {
      closeNodeMenu()
    }
  }
}

onMounted(async () => {
  document.addEventListener('click', handleClickOutside)
  
  const workflowId = route.params.id as string
  
  if (workflowId) {
    try {
      await workflowStore.loadWorkflow(workflowId)
    } catch (e) {
      console.error('Failed to load workflow:', e)
      workflowStore.createWorkflow()
    }
  } else {
    workflowStore.createWorkflow()
  }
  
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeyDown)
})

function handleKeyDown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'z') {
    e.preventDefault()
    if (e.shiftKey) {
      workflowStore.redo()
    } else {
      workflowStore.undo()
    }
  }
  if ((e.metaKey || e.ctrlKey) && e.key === 'y') {
    e.preventDefault()
    workflowStore.redo()
  }
  
  if (e.key === 'Delete' || e.key === 'Backspace') {
    if (canvasStore.selectedEdgeId) {
      e.preventDefault()
      workflowStore.deleteEdge(canvasStore.selectedEdgeId)
      canvasStore.clearSelection()
    }
  }
}

function formatTime(timestamp?: number): string {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', { 
    month: '2-digit', 
    day: '2-digit', 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

function saveWorkflow() {
  workflowStore.saveWorkflow()
}

function runWorkflow() {
  workflowStore.execute()
}

function zoomIn() {
  canvasStore.zoom(0.1)
}

function zoomOut() {
  canvasStore.zoom(-0.1)
}

function fitCanvas() {
  if (!workflowStore.currentWorkflow) return
  
  const nodes = Object.values(workflowStore.currentWorkflow.nodes)
  if (nodes.length === 0) {
    canvasStore.resetView()
    return
  }
  
  const nodeWidth = 180
  const nodeHeight = 80
  const padding = 60
  
  let minX = Infinity, minY = Infinity
  let maxX = -Infinity, maxY = -Infinity
  
  nodes.forEach(node => {
    minX = Math.min(minX, node.position.x)
    minY = Math.min(minY, node.position.y)
    maxX = Math.max(maxX, node.position.x + nodeWidth)
    maxY = Math.max(maxY, node.position.y + nodeHeight)
  })
  
  const contentWidth = maxX - minX + padding * 2
  const contentHeight = maxY - minY + padding * 2
  
  const canvasWidth = window.innerWidth - (canvasStore.selectedNodeId ? 320 : 0)
  const canvasHeight = window.innerHeight - 120
  
  const scaleX = canvasWidth / contentWidth
  const scaleY = canvasHeight / contentHeight
  const newScale = Math.min(scaleX, scaleY, 1.5)
  
  const centerX = (minX + maxX) / 2
  const centerY = (minY + maxY) / 2
  
  const offsetX = canvasWidth / 2 - centerX * newScale
  const offsetY = canvasHeight / 2 - centerY * newScale
  
  canvasStore.setScale(newScale)
  canvasStore.setOffset(offsetX, offsetY)
}

function undo() {
  workflowStore.undo()
}

function redo() {
  workflowStore.redo()
}

function handleDragStart(e: DragEvent, nodeType: NodeType) {
  isDragging.value = true
  e.dataTransfer!.setData('application/node-type', nodeType)
  e.dataTransfer!.effectAllowed = 'copy'
}

function handleDragEnd() {
  isDragging.value = false
  showNodeMenu.value = false
}

function addNode(type: NodeType) {
  const canvasCenter = getCanvasCenter()
  workflowStore.addNode(type, canvasCenter)
  showNodeMenu.value = false
}

function getCanvasCenter() {
  const canvasWidth = window.innerWidth - (canvasStore.selectedNodeId ? 320 : 0)
  const canvasHeight = window.innerHeight - 120
  return {
    x: (canvasWidth / 2 - canvasStore.offsetX) / canvasStore.scale,
    y: (canvasHeight / 2 - canvasStore.offsetY) / canvasStore.scale
  }
}

function autoLayout() {
  if (!workflowStore.currentWorkflow) return
  
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'LR', nodesep: 80, ranksep: 120 })
  g.setDefaultEdgeLabel(() => ({}))
  
  Object.values(workflowStore.currentWorkflow.nodes).forEach(node => {
    g.setNode(node.id, { width: 180, height: 80 })
  })
  
  workflowStore.currentWorkflow.edges.forEach(edge => {
    g.setEdge(edge.from, edge.to)
  })
  
  dagre.layout(g)
  
  g.nodes().forEach(nodeId => {
    const node = g.node(nodeId)
    workflowStore.updateNode(nodeId, {
      position: { x: node.x, y: node.y }
    })
  })
}
</script>

<style scoped lang="scss">
.editor {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f8fafc;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: #64748b;
  text-decoration: none;
  font-size: 18px;
  transition: all 0.2s;
  
  &:hover {
    background: #f1f5f9;
    color: #1e293b;
  }
}

.workflow-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.workflow-name-input {
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  padding: 4px 8px;
  margin-left: -8px;
  border-radius: 4px;
  min-width: 200px;
  
  &:hover {
    background: #f8fafc;
  }
  
  &:focus {
    outline: none;
    background: #f1f5f9;
  }
}

.workflow-status {
  font-size: 12px;
  color: #94a3b8;
}

.header-right {
  display: flex;
  gap: 12px;
}

.header-btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #e2e8f0;
  background: white;
  color: #64748b;
  
  &:hover:not(:disabled) {
    background: #f8fafc;
    border-color: #cbd5e1;
  }
  
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  
  &.primary {
    background: #3b82f6;
    border-color: #3b82f6;
    color: white;
    
    &:hover:not(:disabled) {
      background: #2563eb;
      border-color: #2563eb;
    }
  }
}

.editor-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  position: relative;
}

.canvas-wrapper {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  transition: margin-right 0.3s ease;
  
  &.with-panel {
    margin-right: 0;
  }
}

.bottom-toolbar {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  z-index: 1000;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.toolbar-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  position: relative;
  
  &:hover {
    background: #f1f5f9;
    color: #1e293b;
  }
  
  &[title]:hover::before {
    content: attr(title);
    position: absolute;
    bottom: calc(100% + 8px);
    left: 50%;
    transform: translateX(-50%);
    padding: 6px 10px;
    background: #1e293b;
    color: white;
    font-size: 12px;
    font-weight: 400;
    white-space: nowrap;
    border-radius: 6px;
    z-index: 1001;
    pointer-events: none;
  }
  
  &[title]:hover::after {
    content: '';
    position: absolute;
    bottom: calc(100% + 2px);
    left: 50%;
    transform: translateX(-50%);
    border: 6px solid transparent;
    border-top-color: #1e293b;
    z-index: 1001;
    pointer-events: none;
  }
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: #e2e8f0;
}

.zoom-value {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  min-width: 48px;
  text-align: center;
}

.add-node-wrapper {
  position: relative;
}

.add-node-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: #3b82f6;
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
  
  &:hover {
    background: #2563eb;
  }
}
</style>

<style lang="scss">
.node-menu-dropdown {
  background: white;
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  padding: 8px;
  min-width: 180px;
  max-height: 300px;
  overflow-y: auto;
  z-index: 2000;
}

.node-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  
  &:hover {
    background: #f8fafc;
  }
  
  .node-icon {
    width: 24px;
    height: 24px;
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: white;
  }
  
  .node-name {
    font-size: 13px;
    color: #1e293b;
  }
}
</style>
