import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { v4 as uuidv4 } from 'uuid'
import type { Workflow, WorkflowNode, WorkflowEdge, Position, NodeType } from '@/types/workflow'
import { workflowApi } from '@/services/api'

export const useWorkflowStore = defineStore('workflow', () => {
  // State
  const currentWorkflow = ref<Workflow | null>(null)
  const workflows = ref<Workflow[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  
  // Getters
  const nodes = computed(() => currentWorkflow.value?.nodes || {})
  const edges = computed(() => currentWorkflow.value?.edges || [])
  const nodeList = computed(() => Object.values(nodes.value))
  
  // Actions
  
  // 创建新工作流
  function createWorkflow(name: string = 'New Workflow') {
    const id = uuidv4()
    const startNodeId = 'start_' + uuidv4().slice(0, 8)
    
    currentWorkflow.value = {
      id,
      name,
      entryPoint: startNodeId,
      nodes: {
        [startNodeId]: {
          id: startNodeId,
          name: 'Start',
          type: 'start' as NodeType,
          config: {},
          position: { x: 100, y: 300 }
        }
      },
      edges: [],
      createdAt: Date.now(),
      updatedAt: Date.now()
    }
    
    return currentWorkflow.value
  }
  
  // 加载工作流
  async function loadWorkflow(id: string) {
    isLoading.value = true
    error.value = null
    
    try {
      const workflow = await workflowApi.get(id)
      
      // 确保边有 id 字段
      if (workflow.edges) {
        workflow.edges = workflow.edges.map(edge => ({
          ...edge,
          id: edge.id || `${edge.from}-${edge.to}`
        }))
      }
      
      currentWorkflow.value = workflow
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load workflow'
      throw err
    } finally {
      isLoading.value = false
    }
  }
  
  // 保存工作流
  async function saveWorkflow() {
    if (!currentWorkflow.value) return
    
    isLoading.value = true
    error.value = null
    
    try {
      currentWorkflow.value.updatedAt = Date.now()
      await workflowApi.update(currentWorkflow.value.id, currentWorkflow.value)
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to save workflow'
      throw err
    } finally {
      isLoading.value = false
    }
  }
  
  // 添加节点
  function addNode(type: NodeType, position: Position, name?: string) {
    if (!currentWorkflow.value) return null
    
    const id = `${type}_${uuidv4().slice(0, 8)}`
    const nodeNames: Record<string, string> = {
      start: 'Start',
      end: 'End',
      llm: 'LLM',
      agent: 'Agent',
      condition: 'Condition',
      knowledge_retrieval: 'Knowledge',
      code: 'Code',
      http: 'HTTP',
      template: 'Template',
      variable_assigner: 'Variable'
    }
    
    const node: WorkflowNode = {
      id,
      name: name || nodeNames[type] || type,
      type,
      config: {},
      position: { ...position }
    }
    
    currentWorkflow.value.nodes[id] = node
    currentWorkflow.value.updatedAt = Date.now()
    
    return node
  }
  
  // 更新节点
  function updateNode(id: string, updates: Partial<WorkflowNode>) {
    if (!currentWorkflow.value || !currentWorkflow.value.nodes[id]) return
    
    Object.assign(currentWorkflow.value.nodes[id], updates)
    currentWorkflow.value.updatedAt = Date.now()
  }
  
  // 删除节点
  function deleteNode(id: string) {
    if (!currentWorkflow.value) return
    
    // 删除节点
    delete currentWorkflow.value.nodes[id]
    
    // 删除相关的边
    currentWorkflow.value.edges = currentWorkflow.value.edges.filter(
      edge => edge.from !== id && edge.to !== id
    )
    
    currentWorkflow.value.updatedAt = Date.now()
  }
  
  // 复制节点
  function duplicateNode(nodeId: string): WorkflowNode | null {
    if (!currentWorkflow.value || !currentWorkflow.value.nodes[nodeId]) return null
    
    const sourceNode = currentWorkflow.value.nodes[nodeId]
    const newId = `${sourceNode.type}_${uuidv4().slice(0, 8)}`
    
    const newNode: WorkflowNode = {
      id: newId,
      name: `${sourceNode.name} (复制)`,
      type: sourceNode.type,
      config: JSON.parse(JSON.stringify(sourceNode.config)), // 深拷贝配置
      position: {
        x: sourceNode.position.x + 40,
        y: sourceNode.position.y + 40
      }
    }
    
    currentWorkflow.value.nodes[newId] = newNode
    currentWorkflow.value.updatedAt = Date.now()
    
    return newNode
  }
  
  // 添加边
  function addEdge(from: string, to: string, type: 'normal' | 'conditional' = 'normal') {
    if (!currentWorkflow.value) return null
    
    // 检查是否已存在
    const exists = currentWorkflow.value.edges.some(
      edge => edge.from === from && edge.to === to
    )
    
    if (exists) return null
    
    const edge: WorkflowEdge = {
      id: uuidv4(),
      from,
      to,
      type
    }
    
    currentWorkflow.value.edges.push(edge)
    currentWorkflow.value.updatedAt = Date.now()
    
    return edge
  }
  
  // 删除边
  function deleteEdge(id: string) {
    if (!currentWorkflow.value) return
    
    currentWorkflow.value.edges = currentWorkflow.value.edges.filter(
      edge => edge.id !== id
    )
    
    currentWorkflow.value.updatedAt = Date.now()
  }
  
  // 更新节点配置
  function updateNodeConfig(nodeId: string, config: Record<string, any>) {
    if (!currentWorkflow.value || !currentWorkflow.value.nodes[nodeId]) return
    
    currentWorkflow.value.nodes[nodeId].config = {
      ...currentWorkflow.value.nodes[nodeId].config,
      ...config
    }
    currentWorkflow.value.updatedAt = Date.now()
  }
  
  // 设置入口点
  function setEntryPoint(nodeId: string) {
    if (!currentWorkflow.value) return
    currentWorkflow.value.entryPoint = nodeId
  }
  
  // 执行工作流
  async function execute(inputs: Record<string, any> = {}) {
    if (!currentWorkflow.value) return null
    
    isLoading.value = true
    error.value = null
    
    try {
      const result = await workflowApi.execute(currentWorkflow.value.id, inputs)
      return result
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Execution failed'
      throw err
    } finally {
      isLoading.value = false
    }
  }
  
  // 获取所有工作流列表
  async function fetchWorkflows() {
    isLoading.value = true
    
    try {
      workflows.value = await workflowApi.list()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to fetch workflows'
    } finally {
      isLoading.value = false
    }
  }
  
  return {
    // State
    currentWorkflow,
    workflows,
    isLoading,
    error,
    
    // Getters
    nodes,
    edges,
    nodeList,
    
    // Actions
    createWorkflow,
    loadWorkflow,
    saveWorkflow,
    addNode,
    updateNode,
    deleteNode,
    duplicateNode,
    addEdge,
    deleteEdge,
    updateNodeConfig,
    setEntryPoint,
    execute,
    fetchWorkflows
  }
})
