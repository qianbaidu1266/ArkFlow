import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CanvasState, Position } from '@/types/workflow'

export const useCanvasStore = defineStore('canvas', () => {
  const scale = ref(1)
  const offsetX = ref(0)
  const offsetY = ref(0)
  const isDragging = ref(false)
  const isDraggingNode = ref(false)
  const isConnecting = ref(false)
  const connectingFrom = ref<string | null>(null)
  const selectedNodeId = ref<string | null>(null)
  const selectedEdgeId = ref<string | null>(null)
  const editingNodeId = ref<string | null>(null)
  const dragStart = ref<Position | null>(null)
  const dragOffset = ref<Position>({ x: 0, y: 0 })
  
  // Getters
  const transform = computed(() => ({
    scale: scale.value,
    offsetX: offsetX.value,
    offsetY: offsetY.value
  }))
  
  const canvasState = computed<CanvasState>(() => ({
    scale: scale.value,
    offsetX: offsetX.value,
    offsetY: offsetY.value,
    isDragging: isDragging.value,
    isConnecting: isConnecting.value,
    connectingFrom: connectingFrom.value,
    selectedNodeId: selectedNodeId.value,
    selectedEdgeId: selectedEdgeId.value
  }))
  
  // Actions
  
  // 缩放
  function zoom(delta: number, centerX: number = 0, centerY: number = 0) {
    const oldScale = scale.value
    const newScale = Math.max(0.25, Math.min(3, scale.value + delta))
    
    // 以鼠标位置为中心缩放
    if (centerX !== 0 || centerY !== 0) {
      offsetX.value = centerX - (centerX - offsetX.value) * (newScale / oldScale)
      offsetY.value = centerY - (centerY - offsetY.value) * (newScale / oldScale)
    }
    
    scale.value = newScale
  }
  
  // 设置缩放
  function setScale(newScale: number) {
    scale.value = Math.max(0.25, Math.min(3, newScale))
  }
  
  // 平移
  function pan(deltaX: number, deltaY: number) {
    offsetX.value += deltaX
    offsetY.value += deltaY
  }
  
  // 设置偏移
  function setOffset(x: number, y: number) {
    offsetX.value = x
    offsetY.value = y
  }
  
  // 重置视图
  function resetView() {
    scale.value = 1
    offsetX.value = 0
    offsetY.value = 0
  }
  
  // 选择节点
  function selectNode(nodeId: string | null) {
    selectedNodeId.value = nodeId
    if (nodeId) {
      selectedEdgeId.value = null
    }
  }
  
  function selectEdge(edgeId: string | null) {
    selectedEdgeId.value = edgeId
    if (edgeId) {
      selectedNodeId.value = null
    }
  }
  
  function setEditingNode(nodeId: string | null) {
    editingNodeId.value = nodeId
  }
  
  function clearSelection() {
    selectedNodeId.value = null
    selectedEdgeId.value = null
    editingNodeId.value = null
  }
  
  // 开始画布拖拽
  function startDragging(startPos: Position) {
    isDragging.value = true
    dragStart.value = startPos
  }
  
  // 开始节点拖拽
  function startDraggingNode(startPos: Position, nodeOffset: Position) {
    isDraggingNode.value = true
    dragStart.value = startPos
    dragOffset.value = nodeOffset
  }
  
  // 停止拖拽
  function stopDragging() {
    isDragging.value = false
    isDraggingNode.value = false
    dragStart.value = null
  }
  
  // 开始连接
  function startConnecting(fromNodeId: string) {
    isConnecting.value = true
    connectingFrom.value = fromNodeId
  }
  
  // 停止连接
  function stopConnecting() {
    isConnecting.value = false
    connectingFrom.value = null
  }
  
  function screenToCanvas(screenX: number, screenY: number): Position {
    return {
      x: (screenX - offsetX.value) / scale.value,
      y: (screenY - offsetY.value) / scale.value
    }
  }
  
  function canvasToScreen(canvasX: number, canvasY: number): Position {
    return {
      x: canvasX * scale.value + offsetX.value,
      y: canvasY * scale.value + offsetY.value
    }
  }
  
  return {
    scale,
    offsetX,
    offsetY,
    isDragging,
    isDraggingNode,
    isConnecting,
    connectingFrom,
    selectedNodeId,
    selectedEdgeId,
    editingNodeId,
    dragStart,
    dragOffset,
    
    transform,
    canvasState,
    
    zoom,
    setScale,
    pan,
    setOffset,
    resetView,
    selectNode,
    selectEdge,
    setEditingNode,
    clearSelection,
    startDragging,
    startDraggingNode,
    stopDragging,
    startConnecting,
    stopConnecting,
    screenToCanvas,
    canvasToScreen
  }
})
