<template>
  <div 
    ref="canvasRef"
    class="workflow-canvas"
    @mousedown="handleMouseDown"
    @mousemove="handleMouseMove"
    @mouseup="handleMouseUp"
    @wheel="handleWheel"
    @dragover="handleDragOver"
    @drop="handleDrop"
    @click="handleCanvasClick"
  >
    <!-- 画布内容 -->
    <div 
      class="canvas-content"
      :style="canvasStyle"
    >
      <!-- 连接线 -->
      <svg class="connections-layer">
        <ConnectionLine
          v-for="edge in workflowStore.edges"
          :key="edge.id"
          :edge="edge"
          :from-node="workflowStore.nodes[edge.from]"
          :to-node="workflowStore.nodes[edge.to]"
          :selected="canvasStore.selectedEdgeId === edge.id"
          @click="selectEdge(edge.id, $event)"
        />
        
        <!-- 拖拽中的连接线 -->
        <path
          v-if="canvasStore.isConnecting && dragLine"
          :d="dragLine"
          fill="none"
          stroke="#3b82f6"
          stroke-width="2"
          stroke-dasharray="5,5"
        />
      </svg>
      
      <!-- 节点 -->
      <WorkflowNode
        v-for="node in workflowStore.nodeList"
        :key="node.id"
        :node="node"
        :selected="canvasStore.selectedNodeId === node.id"
        @mousedown="handleNodeMouseDown($event, node)"
        @port-mousedown="handlePortMouseDown($event, node)"
        @port-mouseup="handlePortMouseUp($event, node)"
        @delete="deleteNode(node.id)"
        @copy="copyNode(node)"
        @edit="editNode(node.id)"
      />
    </div>
    
    <!-- 缩放控制 -->
    <div class="zoom-controls">
      <button class="zoom-btn" @click="canvasStore.zoom(0.1)">+</button>
      <span class="zoom-value">{{ Math.round(canvasStore.scale * 100) }}%</span>
      <button class="zoom-btn" @click="canvasStore.zoom(-0.1)">−</button>
      <button class="zoom-btn reset-btn" @click="resetView" title="重置视图">⟲</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useWorkflowStore } from '@/stores/workflow'
import { useCanvasStore } from '@/stores/canvas'
import type { WorkflowNode as WorkflowNodeType, Position, NodeType } from '@/types/workflow'
import WorkflowNode from './WorkflowNode.vue'
import ConnectionLine from './ConnectionLine.vue'

const canvasRef = ref<HTMLElement>()
const workflowStore = useWorkflowStore()
const canvasStore = useCanvasStore()

function deleteNode(nodeId: string) {
  workflowStore.deleteNode(nodeId)
  canvasStore.clearSelection()
}

function copyNode(node: WorkflowNodeType) {
  const newNode = workflowStore.duplicateNode(node.id)
  if (newNode) {
    canvasStore.selectNode(newNode.id)
  }
}

function editNode(nodeId: string) {
  canvasStore.setEditingNode(nodeId)
}

// 拖拽线
const dragLine = computed(() => {
  if (!canvasStore.isConnecting || !canvasStore.connectingFrom) return null
  
  const fromNode = workflowStore.nodes[canvasStore.connectingFrom]
  if (!fromNode) return null
  
  const fromX = fromNode.position.x + 180  // 节点宽度
  const fromY = fromNode.position.y + 40   // 节点高度的一半
  
  // 使用鼠标位置或默认位置
  const toX = canvasStore.dragStart?.x || fromX + 100
  const toY = canvasStore.dragStart?.y || fromY
  
  return generatePath(fromX, fromY, toX, toY)
})

// 画布样式
const canvasStyle = computed(() => ({
  transform: `translate(${canvasStore.offsetX}px, ${canvasStore.offsetY}px) scale(${canvasStore.scale})`,
  transformOrigin: '0 0'
}))

// 生成贝塞尔曲线路径
function generatePath(x1: number, y1: number, x2: number, y2: number): string {
  const midX = (x1 + x2) / 2
  return `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`
}

// 处理画布鼠标按下
function handleMouseDown(e: MouseEvent) {
  const target = e.target as HTMLElement
  
  // 检查是否点击在节点或连线内部
  const isOnNode = target.closest('.workflow-node')
  const isOnEdge = target.closest('.connection-line')
  const isOnPort = target.closest('.node-port')
  const isOnZoomControl = target.closest('.zoom-controls')
  
  // 如果不是点击节点、连线、端口或缩放控件，则开始画布拖拽
  if (!isOnNode && !isOnEdge && !isOnPort && !isOnZoomControl) {
    canvasStore.startDragging({ x: e.clientX, y: e.clientY })
  }
}

// 处理鼠标移动
function handleMouseMove(e: MouseEvent) {
  if (canvasStore.isDraggingNode && canvasStore.selectedNodeId) {
    // 节点拖拽
    const node = workflowStore.nodes[canvasStore.selectedNodeId]
    if (node && canvasStore.dragStart) {
      const canvasPos = canvasStore.screenToCanvas(e.clientX, e.clientY)
      workflowStore.updateNode(node.id, {
        position: {
          x: canvasPos.x - canvasStore.dragOffset.x,
          y: canvasPos.y - canvasStore.dragOffset.y
        }
      })
    }
  } else if (canvasStore.isDragging && canvasStore.dragStart) {
    // 画布拖拽
    const deltaX = e.clientX - canvasStore.dragStart.x
    const deltaY = e.clientY - canvasStore.dragStart.y
    canvasStore.pan(deltaX, deltaY)
    canvasStore.dragStart = { x: e.clientX, y: e.clientY }
  } else if (canvasStore.isConnecting) {
    const rect = canvasRef.value?.getBoundingClientRect()
    if (rect) {
      const canvasPos = canvasStore.screenToCanvas(e.clientX - rect.left, e.clientY - rect.top)
      canvasStore.dragStart = canvasPos
    }
  }
}

// 处理鼠标释放
function handleMouseUp() {
  canvasStore.stopDragging()
  canvasStore.stopConnecting()
}

// 处理滚轮缩放
function handleWheel(e: WheelEvent) {
  e.preventDefault()
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  canvasStore.zoom(delta, e.clientX, e.clientY)
}

// 重置视图 - 将画布居中到节点区域
function resetView() {
  const nodes = workflowStore.nodeList
  if (nodes.length === 0) {
    canvasStore.resetView()
    return
  }
  
  // 计算所有节点的边界框
  let minX = Infinity, minY = Infinity
  let maxX = -Infinity, maxY = -Infinity
  
  nodes.forEach(node => {
    minX = Math.min(minX, node.position.x)
    minY = Math.min(minY, node.position.y)
    maxX = Math.max(maxX, node.position.x + 180) // 节点宽度
    maxY = Math.max(maxY, node.position.y + 80)  // 节点高度
  })
  
  // 计算中心点
  const centerX = (minX + maxX) / 2
  const centerY = (minY + maxY) / 2
  
  // 获取画布尺寸
  const canvasRect = canvasRef.value?.getBoundingClientRect()
  if (!canvasRect) return
  
  // 计算偏移量，使节点区域居中
  const offsetX = canvasRect.width / 2 - centerX
  const offsetY = canvasRect.height / 2 - centerY
  
  canvasStore.setScale(1)
  canvasStore.setOffset(offsetX, offsetY)
}

// 处理节点鼠标按下
function handleNodeMouseDown(e: MouseEvent, node: WorkflowNodeType) {
  e.stopPropagation()
  canvasStore.selectNode(node.id)
  
  // 计算拖拽偏移
  const canvasPos = canvasStore.screenToCanvas(e.clientX, e.clientY)
  const offsetX = canvasPos.x - node.position.x
  const offsetY = canvasPos.y - node.position.y
  
  canvasStore.startDraggingNode({ x: e.clientX, y: e.clientY }, { x: offsetX, y: offsetY })
}

// 处理连接点鼠标按下
function handlePortMouseDown(e: MouseEvent, node: WorkflowNodeType) {
  e.stopPropagation()
  const rect = canvasRef.value?.getBoundingClientRect()
  if (rect) {
    const canvasPos = canvasStore.screenToCanvas(e.clientX - rect.left, e.clientY - rect.top)
    canvasStore.dragStart = canvasPos
  }
  canvasStore.startConnecting(node.id)
}

// 处理连接点鼠标释放
function handlePortMouseUp(e: MouseEvent, node: WorkflowNodeType) {
  e.stopPropagation()
  
  if (canvasStore.isConnecting && canvasStore.connectingFrom) {
    if (canvasStore.connectingFrom !== node.id) {
      // 创建连接
      workflowStore.addEdge(canvasStore.connectingFrom, node.id)
    }
    canvasStore.stopConnecting()
  }
}

// 选择边
function selectEdge(edgeId: string, e: MouseEvent) {
  e.stopPropagation()
  e.preventDefault()
  canvasStore.selectEdge(edgeId)
}

// 处理画布点击
function handleCanvasClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.closest('.connection-line') || target.closest('.workflow-node')) {
    return
  }
  canvasStore.clearSelection()
}

// 处理拖拽悬停
function handleDragOver(e: DragEvent) {
  e.preventDefault()
  e.dataTransfer!.dropEffect = 'copy'
}

// 处理放置
function handleDrop(e: DragEvent) {
  e.preventDefault()
  
  const nodeType = e.dataTransfer!.getData('application/node-type') as NodeType
  if (!nodeType) return
  
  // 计算放置位置
  const rect = canvasRef.value!.getBoundingClientRect()
  const screenX = e.clientX - rect.left
  const screenY = e.clientY - rect.top
  const canvasPos = canvasStore.screenToCanvas(screenX, screenY)
  
  // 创建节点
  workflowStore.addNode(nodeType, canvasPos)
}
</script>

<style scoped>
.workflow-canvas {
  position: relative;
  flex: 1;
  background-color: #f8fafc;
  background-image: radial-gradient(circle, #e2e8f0 1px, transparent 1px);
  background-size: 20px 20px;
  overflow: hidden;
  cursor: grab;
  
  &:active {
    cursor: grabbing;
  }
}

.canvas-content {
  position: absolute;
  width: 100%;
  height: 100%;
}

.connections-layer {
  position: absolute;
  width: 100%;
  height: 100%;
  overflow: visible;
}

.zoom-controls {
  position: absolute;
  bottom: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: white;
  padding: 4px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.zoom-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 18px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
  
  &:hover {
    background: #f1f5f9;
    color: #1e293b;
  }
}

.reset-btn {
  margin-left: 4px;
  border-left: 1px solid #e2e8f0;
  padding-left: 8px;
}

.zoom-value {
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  min-width: 50px;
  text-align: center;
}
</style>
