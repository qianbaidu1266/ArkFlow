// 节点类型
export enum NodeType {
  START = 'start',
  END = 'end',
  LLM = 'llm',
  AGENT = 'agent',
  CONDITION = 'condition',
  KNOWLEDGE_RETRIEVAL = 'knowledge_retrieval',
  CODE = 'code',
  HTTP = 'http',
  TEMPLATE = 'template',
  VARIABLE_ASSIGNER = 'variable_assigner'
}

// 节点类型信息
export interface NodeTypeInfo {
  code: NodeType
  name: string
  description: string
  icon: string
  color: string
  category: string
}

// 节点位置
export interface Position {
  x: number
  y: number
}

// 工作流节点
export interface WorkflowNode {
  id: string
  name: string
  type: NodeType
  config: Record<string, any>
  position: Position
}

// 工作流边
export interface WorkflowEdge {
  id: string
  from: string
  to: string
  type: 'normal' | 'conditional'
  conditions?: Record<string, string>
  defaultTarget?: string
}

// 工作流
export interface Workflow {
  id: string
  name: string
  description?: string
  entryPoint: string
  nodes: Record<string, WorkflowNode>
  edges: WorkflowEdge[]
  createdAt?: number
  updatedAt?: number
  nodeCount?: number
  edgeCount?: number
}

// 执行结果
export interface ExecutionResult {
  executionId: string
  workflowId: string
  success: boolean
  output?: Record<string, any>
  error?: string
  duration: number
  startTime: number
  endTime: number
}

// 画布状态
export interface CanvasState {
  scale: number
  offsetX: number
  offsetY: number
  isDragging: boolean
  isConnecting: boolean
  connectingFrom: string | null
  selectedNodeId: string | null
  selectedEdgeId: string | null
}

// 节点配置定义
export interface NodeConfigDef {
  name: string
  type: 'string' | 'number' | 'boolean' | 'select' | 'textarea' | 'json' | 'array'
  label: string
  description?: string
  required?: boolean
  defaultValue?: any
  options?: { label: string; value: any }[]
}
