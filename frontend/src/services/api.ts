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

// ========== 知识库API ==========

export interface KnowledgeBase {
  id: string
  name: string
  description: string
  embeddingModel: string
  embeddingDimensions: number
  searchType: string
  enableBM25: boolean
  enableReranker: boolean
  chunkSize: number
  chunkOverlap: number
  scoreThreshold: number
  topK: number
  docCount?: number
  chunkCount?: number
  createdAt: number
  updatedAt: number
}

export interface Document {
  id: string
  knowledgeBaseId: string
  title: string
  sourceFileName: string
  sourceFilePath: string
  fileType: string
  fileSize: number
  wordCount: number
  indexingStatus: string
  chunkCount: number
  errorMessage: string | null
  createdAt: number
  updatedAt: number
}

export interface Chunk {
  id: string
  documentId: string
  knowledgeBaseId: string
  content: string
  chunkIndex: number
  tokenCount: number
  wordCount: number
  milvusChunkId: string
  createdAt: number
  updatedAt: number
}

export interface SearchResult {
  id: string
  documentId: string
  content: string
  chunkIndex: number
  score: number
}

export const knowledgeApi = {
  // 知识库 CRUD
  async list(): Promise<KnowledgeBase[]> {
    const res = await api.get('/knowledge-bases')
    return res.data
  },

  async get(id: string): Promise<KnowledgeBase> {
    const res = await api.get(`/knowledge-bases/${id}`)
    return res.data
  },

  async create(data: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
    const res = await api.post('/knowledge-bases', data)
    return res.data
  },

  async update(id: string, data: Partial<KnowledgeBase>): Promise<KnowledgeBase> {
    const res = await api.put(`/knowledge-bases/${id}`, data)
    return res.data
  },

  async delete(id: string): Promise<void> {
    await api.delete(`/knowledge-bases/${id}`)
  },

  // 文档管理
  async listDocuments(kbId: string, offset = 0, limit = 20): Promise<Document[]> {
    const res = await api.get(`/knowledge-bases/${kbId}/documents`, {
      params: { offset, limit }
    })
    return res.data
  },

  async getDocument(kbId: string, docId: string): Promise<Document> {
    const res = await api.get(`/knowledge-bases/${kbId}/documents/${docId}`)
    return res.data
  },

  async uploadDocument(kbId: string, file: File): Promise<{ id: string; status: string }> {
    const formData = new FormData()
    formData.append('file', file)
    const res = await api.post(`/knowledge-bases/${kbId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return res.data
  },

  async deleteDocument(kbId: string, docId: string): Promise<void> {
    await api.delete(`/knowledge-bases/${kbId}/documents/${docId}`)
  },

  // 分块管理
  async listChunks(kbId: string, docId: string): Promise<Chunk[]> {
    const res = await api.get(`/knowledge-bases/${kbId}/documents/${docId}/chunks`)
    return res.data
  },

  async updateChunk(kbId: string, docId: string, chunkId: string, content: string): Promise<void> {
    await api.put(`/knowledge-bases/${kbId}/documents/${docId}/chunks/${chunkId}`, { content })
  },

  // 检索
  async search(kbId: string, query: string, topK = 5, searchType = 'hybrid'): Promise<SearchResult[]> {
    const res = await api.post(`/knowledge-bases/${kbId}/search`, {
      query,
      topK,
      searchType
    })
    return res.data
  }
}
