<template>
  <Teleport to="body">
    <Transition name="slide">
      <div v-if="visible" class="execution-panel">
        <div class="panel-header">
          <div class="header-left">
            <span class="run-icon">▶</span>
            <span class="header-title">运行</span>
          </div>
          <button class="btn-close" @click="close">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M12 4L4 12M4 4L12 12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        
        <div class="panel-tabs">
          <button 
            v-for="tab in tabs" 
            :key="tab.key"
            class="tab-btn"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
        
        <div class="panel-body">
          <div v-if="activeTab === 'input'" class="tab-content">
            <div v-if="inputVariables.length > 0" class="input-section">
              <div class="section-label">输入变量</div>
              <div class="input-list">
                <div v-for="variable in inputVariables" :key="variable" class="input-item">
                  <label class="input-label">{{ variable }}</label>
                  <input
                    v-model="inputs[variable]"
                    type="text"
                    class="input-field"
                    :placeholder="`请输入 ${variable}`"
                  />
                </div>
              </div>
            </div>
            
            <div v-else class="empty-input">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="12" cy="12" r="10"/>
                <path d="M12 16v-4M12 8h.01"/>
              </svg>
              <span>无需输入参数</span>
            </div>
            
            <div v-if="isExecuting" class="executing-overlay">
              <div class="executing-spinner"></div>
              <span>正在执行...</span>
            </div>
          </div>
          
          <div v-if="activeTab === 'result'" class="tab-content">
            <div v-if="!result && !isExecuting" class="empty-state">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
              </svg>
              <span>暂无执行结果</span>
              <span class="hint">点击下方运行按钮开始执行</span>
            </div>
            
            <div v-else-if="isExecuting" class="loading-state">
              <div class="loading-spinner"></div>
              <span>正在执行工作流...</span>
            </div>
            
            <div v-else-if="result" class="result-content">
              <div class="result-status" :class="result.success ? 'success' : 'error'">
                <div class="status-icon">
                  <svg v-if="result.success" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="15" y1="9" x2="9" y2="15"/>
                    <line x1="9" y1="9" x2="15" y2="15"/>
                  </svg>
                </div>
                <span class="status-text">{{ result.success ? '执行成功' : '执行失败' }}</span>
                <span class="status-duration">{{ result.duration }}ms</span>
              </div>
              
              <div v-if="result.error" class="error-section">
                <div class="section-label">错误信息</div>
                <pre class="error-text">{{ result.error }}</pre>
              </div>
              
              <div v-if="result.output" class="output-section">
                <div class="section-label">输出结果</div>
                <div class="output-content">
                  <pre>{{ formatOutput(result.output) }}</pre>
                </div>
              </div>
            </div>
          </div>
          
          <div v-if="activeTab === 'details'" class="tab-content">
            <div v-if="!result" class="empty-state">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <line x1="3" y1="9" x2="21" y2="9"/>
                <line x1="9" y1="21" x2="9" y2="9"/>
              </svg>
              <span>暂无执行详情</span>
            </div>
            
            <div v-else class="details-content">
              <div class="detail-row">
                <span class="detail-label">执行ID</span>
                <span class="detail-value code">{{ result.executionId }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">工作流ID</span>
                <span class="detail-value code">{{ result.workflowId }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">状态</span>
                <span class="detail-value" :class="result.success ? 'text-success' : 'text-error'">
                  {{ result.success ? '成功' : '失败' }}
                </span>
              </div>
              <div class="detail-row">
                <span class="detail-label">耗时</span>
                <span class="detail-value">{{ result.duration }}ms</span>
              </div>
            </div>
          </div>
          
          <div v-if="activeTab === 'trace'" class="tab-content">
            <div v-if="!snapshots.length && !isExecuting" class="empty-state">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              <span>暂无执行追踪</span>
            </div>
            
            <div v-else-if="isExecuting && !snapshots.length" class="loading-state">
              <div class="loading-spinner"></div>
              <span>正在收集追踪信息...</span>
            </div>
            
            <div v-else class="trace-content">
              <div class="trace-list">
                <div 
                  v-for="(snapshot, index) in snapshots" 
                  :key="snapshot.id"
                  class="trace-item"
                  :class="getStatusClass(snapshot.status)"
                >
                  <div class="trace-header" @click="toggleTrace(index)">
                    <div class="trace-left">
                      <div class="node-icon" :style="{ background: getNodeColor(snapshot.nodeType) }">
                        {{ getNodeIcon(snapshot.nodeType) }}
                      </div>
                      <div class="node-info">
                        <span class="node-name">{{ snapshot.nodeName || snapshot.nodeId }}</span>
                        <span class="node-type">{{ getNodeTypeName(snapshot.nodeType) }}</span>
                      </div>
                    </div>
                    <div class="trace-right">
                      <span class="trace-status" :class="getStatusClass(snapshot.status)">
                        {{ getStatusText(snapshot.status) }}
                      </span>
                      <span class="trace-time">{{ snapshot.duration }}ms</span>
                      <svg 
                        class="expand-icon" 
                        :class="{ expanded: expandedTraces.includes(index) }"
                        width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                      >
                        <polyline points="6 9 12 15 18 9"/>
                      </svg>
                    </div>
                  </div>
                  
                  <Transition name="collapse">
                    <div v-if="expandedTraces.includes(index)" class="trace-body">
                      <div v-if="snapshot.errorMessage" class="trace-error">
                        <div class="error-title">错误信息</div>
                        <pre class="error-text">{{ snapshot.errorMessage }}</pre>
                      </div>
                      
                      <div v-if="snapshot.inputs && Object.keys(snapshot.inputs).length > 0" class="trace-section">
                        <div class="section-title">输入</div>
                        <pre class="section-content">{{ formatOutput(snapshot.inputs) }}</pre>
                      </div>
                      
                      <div v-if="snapshot.outputs && Object.keys(snapshot.outputs).length > 0" class="trace-section">
                        <div class="section-title">输出</div>
                        <pre class="section-content">{{ formatOutput(snapshot.outputs) }}</pre>
                      </div>
                      
                      <div v-if="snapshot.metadata && Object.keys(snapshot.metadata).length > 0" class="trace-section">
                        <div class="section-title">元数据</div>
                        <pre class="section-content">{{ formatOutput(snapshot.metadata) }}</pre>
                      </div>
                    </div>
                  </Transition>
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <div class="panel-footer">
          <template v-if="!isExecuting && !result">
            <button class="btn-secondary" @click="close">取消</button>
            <button class="btn-primary" :disabled="!canExecute" @click="execute">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="5 3 19 12 5 21 5 3"/>
              </svg>
              运行
            </button>
          </template>
          
          <template v-if="result">
            <button class="btn-secondary" @click="close">关闭</button>
            <button class="btn-primary" @click="resetAndRun">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10"/>
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
              </svg>
              重新运行
            </button>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { workflowApi } from '@/services/api'
import { createExecutionWebSocket, type ExecutionEvent, type ExecutionWebSocket } from '@/services/websocket'
import type { Workflow, ExecutionResult } from '@/types/workflow'

interface NodeSnapshot {
  id: string
  nodeId: string
  nodeType: string
  nodeName: string
  status: string
  startTime: number
  endTime: number
  duration: number
  inputs: Record<string, any>
  outputs: Record<string, any>
  errorMessage: string
  metadata: Record<string, any>
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

const props = defineProps<{
  visible: boolean
  workflow: Workflow | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'executed', result: ExecutionResult): void
  (e: 'nodeStatus', nodeId: string, status: string): void
  (e: 'executionStarted'): void
  (e: 'executionCompleted', success: boolean): void
}>()

const tabs = [
  { key: 'input', label: '输入' },
  { key: 'result', label: '结果' },
  { key: 'details', label: '详情' },
  { key: 'trace', label: '追踪' }
]

const activeTab = ref('input')
const inputs = ref<Record<string, string>>({})
const isExecuting = ref(false)
const result = ref<ExecutionResult | null>(null)
const snapshots = ref<NodeSnapshot[]>([])
const expandedTraces = ref<number[]>([])
let ws: ExecutionWebSocket | null = null

const inputVariables = computed(() => {
  if (!props.workflow) return []
  const startNode = Object.values(props.workflow.nodes).find(n => n.type === 'start')
  if (!startNode) return []
  return startNode.config?.inputVariables || []
})

const canExecute = computed(() => {
  return inputVariables.value.every(v => inputs.value[v]?.trim())
})

watch(() => props.visible, (visible) => {
  if (visible) {
    inputs.value = {}
    result.value = null
    isExecuting.value = false
    snapshots.value = []
    expandedTraces.value = []
    activeTab.value = 'input'
  }
})

onUnmounted(() => {
  if (ws) {
    ws.close()
    ws = null
  }
})

function handleWebSocketEvent(event: ExecutionEvent) {
  console.log('WebSocket event:', event)
  
  switch (event.type) {
    case 'execution_started':
      emit('executionStarted')
      break
      
    case 'node_started':
      const existingIndex = snapshots.value.findIndex(s => s.nodeId === event.nodeId)
      if (existingIndex === -1) {
        snapshots.value.push({
          id: `snapshot-${Date.now()}`,
          nodeId: event.nodeId || '',
          nodeType: event.nodeType || '',
          nodeName: event.nodeName || '',
          status: 'RUNNING',
          startTime: event.timestamp || Date.now(),
          endTime: 0,
          duration: 0,
          inputs: {},
          outputs: {},
          errorMessage: '',
          metadata: {},
          promptTokens: 0,
          completionTokens: 0,
          totalTokens: 0
        })
      }
      emit('nodeStatus', event.nodeId || '', 'RUNNING')
      if (activeTab.value !== 'trace') {
        activeTab.value = 'trace'
      }
      break
      
    case 'node_completed':
      const completedIndex = snapshots.value.findIndex(s => s.nodeId === event.nodeId)
      if (completedIndex !== -1) {
        const snapshot = snapshots.value[completedIndex]
        snapshot.status = event.status || 'SUCCESS'
        snapshot.endTime = event.timestamp || Date.now()
        snapshot.duration = event.duration || 0
        if (event.outputs) {
          snapshot.outputs = event.outputs
        }
      }
      emit('nodeStatus', event.nodeId || '', event.status || 'SUCCESS')
      break
      
    case 'node_failed':
      const failedIndex = snapshots.value.findIndex(s => s.nodeId === event.nodeId)
      if (failedIndex !== -1) {
        const snapshot = snapshots.value[failedIndex]
        snapshot.status = 'FAILED'
        snapshot.endTime = event.timestamp || Date.now()
        snapshot.duration = event.duration || 0
        snapshot.errorMessage = event.errorMessage || ''
      } else {
        // 如果失败节点还没有在列表中，添加它
        snapshots.value.push({
          id: `snapshot-${Date.now()}`,
          nodeId: event.nodeId || '',
          nodeType: event.nodeType || '',
          nodeName: event.nodeName || '',
          status: 'FAILED',
          startTime: event.timestamp || Date.now(),
          endTime: event.timestamp || Date.now(),
          duration: event.duration || 0,
          inputs: {},
          outputs: {},
          errorMessage: event.errorMessage || '',
          metadata: {},
          promptTokens: 0,
          completionTokens: 0,
          totalTokens: 0
        })
      }
      emit('nodeStatus', event.nodeId || '', 'FAILED')
      isExecuting.value = false
      break
      
    case 'execution_completed':
      isExecuting.value = false
      emit('executionCompleted', event.success || false)
      if (event.success) {
        result.value = {
          executionId: event.executionId,
          workflowId: event.workflowId || '',
          success: true,
          duration: event.duration || 0
        } as ExecutionResult
      }
      break
  }
}

async function execute() {
  if (!props.workflow || !canExecute.value) return
  
  isExecuting.value = true
  result.value = null
  snapshots.value = []
  expandedTraces.value = []
  activeTab.value = 'trace'
  
  try {
    const executionId = `exec_${Date.now()}_${Math.random().toString(36).substring(2, 10)}`
    
    ws = createExecutionWebSocket(executionId)
    ws.onEvent(handleWebSocketEvent)
    
    try {
      await ws.connect()
      console.log('WebSocket connected, starting execution...')
    } catch (e) {
      console.warn('WebSocket connection failed, continuing without real-time updates:', e)
    }
    
    const res = await workflowApi.execute(props.workflow.id, inputs.value, {
      enableCheckpoint: true,
      timeout: 60000,
      executionId: executionId
    })
    
    result.value = res
    emit('executed', res)
    
    await loadSnapshots(res.executionId)
  } catch (e: any) {
    result.value = {
      executionId: '',
      workflowId: props.workflow.id,
      success: false,
      error: e.message || '执行失败',
      duration: 0
    } as ExecutionResult
  } finally {
    isExecuting.value = false
  }
}

async function loadSnapshots(executionId: string) {
  try {
    const res = await fetch(`/api/executions/${executionId}/snapshots`)
    if (res.ok) {
      const data = await res.json()
      snapshots.value = data.snapshots || []
    }
  } catch (e) {
    console.error('Failed to load snapshots:', e)
  }
}

function resetAndRun() {
  result.value = null
  snapshots.value = []
  expandedTraces.value = []
  activeTab.value = 'input'
  execute()
}

function close() {
  if (!isExecuting.value) {
    emit('close')
  }
}

function toggleTrace(index: number) {
  const idx = expandedTraces.value.indexOf(index)
  if (idx > -1) {
    expandedTraces.value.splice(idx, 1)
  } else {
    expandedTraces.value.push(index)
  }
}

function formatOutput(output: any): string {
  if (typeof output === 'string') return output
  return JSON.stringify(output, null, 2)
}

function getStatusClass(status: string): string {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'error'
    case 'RUNNING': return 'running'
    case 'PENDING': return 'pending'
    default: return ''
  }
}

function getStatusText(status: string): string {
  switch (status) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'RUNNING': return '运行中'
    case 'PENDING': return '等待中'
    case 'SKIPPED': return '跳过'
    default: return status
  }
}

function getNodeIcon(type: string): string {
  const icons: Record<string, string> = {
    start: '▶',
    end: '■',
    llm: '🤖',
    agent: '🎯',
    condition: '◇',
    knowledge_retrieval: '📚',
    code: '💻',
    http: '🌐',
    variable_assigner: '📝',
    template: '📄',
    tool: '🔧',
    iteration: '🔄',
    parallel: '⚡',
    aggregate: '⊕',
    question_classifier: '🏷',
    rerank: '📊'
  }
  return icons[type] || '●'
}

function getNodeColor(type: string): string {
  const colors: Record<string, string> = {
    start: '#10b981',
    end: '#ef4444',
    llm: '#8b5cf6',
    agent: '#f59e0b',
    condition: '#ec4899',
    knowledge_retrieval: '#06b6d4',
    code: '#6366f1',
    http: '#84cc16',
    variable_assigner: '#14b8a6',
    template: '#f97316',
    tool: '#64748b',
    iteration: '#3b82f6',
    parallel: '#a855f7',
    aggregate: '#22c55e',
    question_classifier: '#eab308',
    rerank: '#0ea5e9'
  }
  return colors[type] || '#64748b'
}

function getNodeTypeName(type: string): string {
  const names: Record<string, string> = {
    start: '开始',
    end: '结束',
    llm: 'LLM',
    agent: 'Agent',
    condition: '条件分支',
    knowledge_retrieval: '知识检索',
    code: '代码执行',
    http: 'HTTP请求',
    variable_assigner: '变量赋值',
    template: '模板',
    tool: '工具调用',
    iteration: '迭代',
    parallel: '并行',
    aggregate: '聚合',
    question_classifier: '问题分类',
    rerank: '重排'
  }
  return names[type] || type
}
</script>

<style scoped>
.execution-panel {
  position: fixed;
  top: 0;
  right: 0;
  width: 420px;
  height: 100vh;
  background: #fff;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 2000;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.run-icon {
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: white;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.btn-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.btn-close:hover {
  background: #f3f4f6;
  color: #374151;
}

.panel-tabs {
  display: flex;
  padding: 0 20px;
  border-bottom: 1px solid #e5e7eb;
  gap: 4px;
}

.tab-btn {
  padding: 12px 16px;
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.2s;
}

.tab-btn:hover {
  color: #374151;
}

.tab-btn.active {
  color: #3b82f6;
  border-bottom-color: #3b82f6;
}

.panel-body {
  flex: 1;
  overflow: auto;
  padding: 20px;
}

.tab-content {
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-label {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.input-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input-label {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.input-field {
  padding: 10px 14px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.2s;
}

.input-field:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.empty-input {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px;
  color: #9ca3af;
  font-size: 14px;
}

.executing-overlay {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px;
  color: #3b82f6;
}

.executing-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e5e7eb;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  color: #9ca3af;
  gap: 12px;
}

.empty-state .hint {
  font-size: 12px;
  color: #d1d5db;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  gap: 16px;
  color: #6b7280;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e5e7eb;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.result-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.result-status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
}

.result-status.success {
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
}

.result-status.error {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
}

.status-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.success .status-icon {
  background: #10b981;
  color: white;
}

.error .status-icon {
  background: #ef4444;
  color: white;
}

.status-text {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
}

.success .status-text { color: #065f46; }
.error .status-text { color: #991b1b; }

.status-duration {
  font-size: 13px;
  font-weight: 500;
}

.success .status-duration { color: #047857; }
.error .status-duration { color: #b91c1c; }

.error-section, .output-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.error-text {
  padding: 12px 16px;
  background: #fef2f2;
  border-radius: 8px;
  color: #991b1b;
  font-size: 13px;
  line-height: 1.5;
  overflow: auto;
  max-height: 200px;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}

.output-content {
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.output-content pre {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
}

.details-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.detail-label {
  width: 80px;
  font-size: 13px;
  color: #6b7280;
}

.detail-value {
  flex: 1;
  font-size: 13px;
  color: #374151;
  font-weight: 500;
}

.detail-value.code {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 12px;
  background: #f3f4f6;
  padding: 4px 8px;
  border-radius: 4px;
}

.text-success { color: #10b981; }
.text-error { color: #ef4444; }

.trace-content {
  padding: 0;
}

.trace-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trace-item {
  background: #f9fafb;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  overflow: hidden;
  transition: all 0.2s;
}

.trace-item:hover {
  border-color: #d1d5db;
}

.trace-item.success { border-left: 3px solid #10b981; }
.trace-item.error { border-left: 3px solid #ef4444; }
.trace-item.running { border-left: 3px solid #3b82f6; }
.trace-item.pending { border-left: 3px solid #9ca3af; }

.trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  cursor: pointer;
  user-select: none;
}

.trace-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.node-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
  flex-shrink: 0;
}

.node-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.node-name {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.node-type {
  font-size: 11px;
  color: #9ca3af;
}

.trace-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.trace-status {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 500;
}

.trace-status.success {
  background: #d1fae5;
  color: #065f46;
}

.trace-status.error {
  background: #fee2e2;
  color: #991b1b;
}

.trace-status.running {
  background: #dbeafe;
  color: #1d4ed8;
}

.trace-status.pending {
  background: #f3f4f6;
  color: #6b7280;
}

.trace-time {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 500;
}

.expand-icon {
  color: #9ca3af;
  transition: transform 0.2s;
}

.expand-icon.expanded {
  transform: rotate(180deg);
}

.trace-body {
  padding: 0 14px 14px;
  border-top: 1px solid #e5e7eb;
  margin-top: 0;
  padding-top: 14px;
}

.trace-error {
  margin-bottom: 12px;
}

.error-title {
  font-size: 11px;
  font-weight: 600;
  color: #ef4444;
  margin-bottom: 6px;
  text-transform: uppercase;
}

.trace-section {
  margin-bottom: 12px;
}

.trace-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 6px;
  text-transform: uppercase;
}

.section-content {
  padding: 10px 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  font-size: 12px;
  color: #374151;
  overflow: auto;
  max-height: 150px;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid #e5e7eb;
  background: #fafafa;
}

.btn-secondary {
  padding: 10px 20px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: white;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}

.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.collapse-enter-from,
.collapse-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.collapse-enter-to,
.collapse-leave-from {
  max-height: 500px;
}
</style>
