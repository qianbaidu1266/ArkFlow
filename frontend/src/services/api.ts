import axios from 'axios'
import type { Workflow, ExecutionResult } from '@/types/workflow'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json'
  }
})

// 工作流API
export const workflowApi = {
  // 获取所有工作流
  async list(): Promise<Workflow[]> {
    const response = await api.get('/workflows')
    return response.data.workflows || []
  },

  // 获取工作流详情
  async get(id: string): Promise<Workflow> {
    const response = await api.get(`/workflows/${id}`)
    return response.data
  },

  // 创建工作流
  async create(workflow: Partial<Workflow>): Promise<{ id: string; status: string }> {
    const response = await api.post('/workflows', workflow)
    return response.data
  },

  // 更新工作流
  async update(id: string, workflow: Partial<Workflow>): Promise<{ id: string; status: string }> {
    const response = await api.put(`/workflows/${id}`, workflow)
    return response.data
  },

  // 删除工作流
  async delete(id: string): Promise<void> {
    await api.delete(`/workflows/${id}`)
  },

  // 执行工作流
  async execute(id: string, inputs: Record<string, any>, config?: Record<string, any>): Promise<ExecutionResult> {
    const response = await api.post(`/workflows/${id}/execute`, {
      inputs,
      config,
      executionId: config?.executionId
    })
    return response.data
  },

  // 获取执行结果
  async getExecution(executionId: string): Promise<ExecutionResult> {
    const response = await api.get(`/executions/${executionId}`)
    return response.data
  },

  // 获取节点类型列表
  async getNodeTypes(): Promise<any[]> {
    const response = await api.get('/node-types')
    return response.data
  },

  // 获取执行历史列表
  async listExecutions(workflowId?: string, offset = 0, limit = 50): Promise<any[]> {
    const params: Record<string, any> = { offset, limit }
    if (workflowId) params.workflowId = workflowId
    const response = await api.get('/executions', { params })
    return response.data.executions || []
  },

  // 获取执行详情（含节点快照）
  async getExecutionDetail(executionId: string): Promise<{ execution: any; snapshots: any[] }> {
    const [executionRes, snapshotsRes] = await Promise.all([
      api.get(`/executions/${executionId}`),
      api.get(`/executions/${executionId}/snapshots`)
    ])
    return {
      execution: executionRes.data,
      snapshots: snapshotsRes.data.snapshots || []
    }
  }
}

export default api
