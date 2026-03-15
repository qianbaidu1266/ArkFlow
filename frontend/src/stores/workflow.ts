import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { v4 as uuidv4 } from 'uuid'
import type { Workflow, WorkflowNode, WorkflowEdge, Position, NodeType } from '@/types/workflow'
import { workflowApi } from '@/services/api'

export const useWorkflowStore = defineStore('workflow', () => {
  const currentWorkflow = ref<Workflow | null>(null)
  const workflows = ref<Workflow[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  
  const history = ref<string[]>([])
  const historyIndex = ref(-1)
  const maxHistory = 50
  
  const nodes = computed(() => currentWorkflow.value?.nodes || {})
  const edges = computed(() => currentWorkflow.value?.edges || [])
  const nodeList = computed(() => Object.values(nodes.value))
  const canUndo = computed(() => historyIndex.value > 0)
  const canRedo = computed(() => historyIndex.value < history.value.length - 1)
  
  function pushHistory() {
    if (!currentWorkflow.value) return
    
    const snapshot = JSON.stringify(currentWorkflow.value)
    
    if (historyIndex.value < history.value.length - 1) {
      history.value = history.value.slice(0, historyIndex.value + 1)
    }
    
    history.value.push(snapshot)
    
    if (history.value.length > maxHistory) {
      history.value.shift()
    } else {
      historyIndex.value++
    }
  }
  
  function undo() {
    if (!canUndo.value) return
    
    historyIndex.value--
    const snapshot = history.value[historyIndex.value]
    if (snapshot) {
      currentWorkflow.value = JSON.parse(snapshot)
    }
  }
  
  function redo() {
    if (!canRedo.value) return
    
    historyIndex.value++
    const snapshot = history.value[historyIndex.value]
    if (snapshot) {
      currentWorkflow.value = JSON.parse(snapshot)
    }
  }
  
  function clearHistory() {
    history.value = []
    historyIndex.value = -1
  }
  
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
    
    clearHistory()
    pushHistory()
    
    return currentWorkflow.value
  }
  
  async function loadWorkflow(id: string) {
    isLoading.value = true
    error.value = null
    
    try {
      const workflow = await workflowApi.get(id)
      
      if (workflow.edges) {
        workflow.edges = workflow.edges.map(edge => ({
          ...edge,
          id: edge.id || `${edge.from}-${edge.to}`
        }))
      }
      
      currentWorkflow.value = workflow
      clearHistory()
      pushHistory()
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load workflow'
      throw err
    } finally {
      isLoading.value = false
    }
  }
  
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
    pushHistory()
    
    return node
  }
  
  function updateNode(id: string, updates: Partial<WorkflowNode>) {
    if (!currentWorkflow.value || !currentWorkflow.value.nodes[id]) return
    
    Object.assign(currentWorkflow.value.nodes[id], updates)
    currentWorkflow.value.updatedAt = Date.now()
    pushHistory()
  }
  
  function deleteNode(id: string) {
    if (!currentWorkflow.value) return
    
    delete currentWorkflow.value.nodes[id]
    currentWorkflow.value.edges = currentWorkflow.value.edges.filter(
      edge => edge.from !== id && edge.to !== id
    )
    currentWorkflow.value.updatedAt = Date.now()
    pushHistory()
  }
  
  function duplicateNode(nodeId: string): WorkflowNode | null {
    if (!currentWorkflow.value || !currentWorkflow.value.nodes[nodeId]) return null
    
    const sourceNode = currentWorkflow.value.nodes[nodeId]
    const newId = `${sourceNode.type}_${uuidv4().slice(0, 8)}`
    
    const newNode: WorkflowNode = {
      id: newId,
      name: `${sourceNode.name} (复制)`,
      type: sourceNode.type,
      config: JSON.parse(JSON.stringify(sourceNode.config)),
      position: {
        x: sourceNode.position.x + 40,
        y: sourceNode.position.y + 40
      }
    }
    
    currentWorkflow.value.nodes[newId] = newNode
    currentWorkflow.value.updatedAt = Date.now()
    pushHistory()
    
    return newNode
  }
  
  function addEdge(from: string, to: string, type: 'normal' | 'conditional' = 'normal') {
    if (!currentWorkflow.value) return null
    
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
    pushHistory()
    
    return edge
  }
  
  function deleteEdge(id: string) {
    if (!currentWorkflow.value) return
    
    currentWorkflow.value.edges = currentWorkflow.value.edges.filter(
      edge => edge.id !== id
    )
    currentWorkflow.value.updatedAt = Date.now()
    pushHistory()
  }
  
  function updateNodeConfig(nodeId: string, config: Record<string, any>) {
    if (!currentWorkflow.value || !currentWorkflow.value.nodes[nodeId]) return
    
    currentWorkflow.value.nodes[nodeId].config = {
      ...currentWorkflow.value.nodes[nodeId].config,
      ...config
    }
    currentWorkflow.value.updatedAt = Date.now()
    pushHistory()
  }
  
  function setEntryPoint(nodeId: string) {
    if (!currentWorkflow.value) return
    currentWorkflow.value.entryPoint = nodeId
    pushHistory()
  }
  
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
    currentWorkflow,
    workflows,
    isLoading,
    error,
    canUndo,
    canRedo,
    nodes,
    edges,
    nodeList,
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
    fetchWorkflows,
    undo,
    redo,
    pushHistory
  }
})
