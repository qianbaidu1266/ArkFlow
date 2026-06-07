<template>
  <div class="execution-history">
    <div class="page-header">
      <h2>运行历史</h2>
      <div class="header-actions">
        <select v-model="filterWorkflowId" class="filter-select" @change="loadExecutions">
          <option value="">全部工作流</option>
          <option v-for="wf in workflows" :key="wf.id" :value="wf.id">{{ wf.name }}</option>
        </select>
        <button class="btn-refresh" @click="loadExecutions">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M13.65 2.35A7.96 7.96 0 0 0 8 0C3.58 0 0 3.58 0 8s3.58 8 8 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 8 14 6 6 0 1 1 8 2c1.66 0 3.14.69 4.22 1.78L9 7h7V0l-2.35 2.35z" fill="currentColor"/>
          </svg>
          刷新
        </button>
      </div>
    </div>

    <!-- 执行列表 -->
    <div v-if="loading" class="loading-state">加载中...</div>
    <div v-else-if="executions.length === 0" class="empty-state">
      <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
        <circle cx="24" cy="24" r="20" stroke="#CBD5E1" stroke-width="2"/>
        <path d="M24 14V24L30 28" stroke="#CBD5E1" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <p>暂无运行记录</p>
      <span>执行工作流后，运行记录将显示在这里</span>
    </div>
    <div v-else class="execution-list">
      <div
        v-for="exec in executions"
        :key="exec.id"
        class="execution-card"
        @click="showDetail(exec)"
      >
        <div class="exec-left">
          <span class="exec-status" :class="statusClass(exec.status)">
            <span class="status-dot"></span>
            {{ statusText(exec.status) }}
          </span>
          <span class="exec-workflow">{{ getWorkflowName(exec.workflowId) }}</span>
        </div>
        <div class="exec-center">
          <span class="exec-id">{{ exec.id.substring(0, 8) }}</span>
          <span class="exec-time">{{ formatTime(exec.startTime) }}</span>
        </div>
        <div class="exec-right">
          <span v-if="exec.duration" class="exec-duration">{{ formatDuration(exec.duration) }}</span>
          <span v-if="exec.totalTokens" class="exec-tokens">{{ exec.totalTokens }} tokens</span>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" class="exec-arrow">
            <path d="M6 4L10 8L6 12" stroke="#94A3B8" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
      </div>
    </div>

    <!-- 执行详情弹窗 -->
    <div v-if="selectedExecution" class="modal-overlay" @click.self="selectedExecution = null">
      <div class="modal-content">
        <div class="modal-header">
          <h3>执行详情</h3>
          <button class="modal-close" @click="selectedExecution = null">
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M15 5L5 15M5 5L15 15" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <!-- 基本信息 -->
          <div class="detail-section">
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">执行 ID</span>
                <span class="detail-value mono">{{ selectedExecution.id }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">状态</span>
                <span class="exec-status" :class="statusClass(selectedExecution.status)">
                  <span class="status-dot"></span>
                  {{ statusText(selectedExecution.status) }}
                </span>
              </div>
              <div class="detail-item">
                <span class="detail-label">工作流</span>
                <span class="detail-value">{{ getWorkflowName(selectedExecution.workflowId) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">开始时间</span>
                <span class="detail-value">{{ formatTime(selectedExecution.startTime) }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">耗时</span>
                <span class="detail-value">{{ selectedExecution.duration ? formatDuration(selectedExecution.duration) : '-' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">Token 用量</span>
                <span class="detail-value">{{ selectedExecution.totalTokens || '-' }}</span>
              </div>
            </div>
            <div v-if="selectedExecution.error" class="error-block">
              <span class="error-label">错误信息</span>
              <pre>{{ selectedExecution.error }}</pre>
            </div>
          </div>

          <!-- 节点执行快照 -->
          <div class="detail-section">
            <h4>节点执行记录</h4>
            <div v-if="detailLoading" class="loading-state">加载中...</div>
            <div v-else-if="snapshots.length === 0" class="empty-hint">暂无节点执行记录</div>
            <div v-else class="snapshot-timeline">
              <div v-for="snapshot in snapshots" :key="snapshot.id" class="snapshot-item">
                <div class="snapshot-dot" :class="statusClass(snapshot.status)"></div>
                <div class="snapshot-card">
                  <div class="snapshot-header">
                    <span class="snapshot-name">{{ snapshot.nodeName }}</span>
                    <span class="snapshot-type">{{ snapshot.nodeType }}</span>
                    <span class="exec-status sm" :class="statusClass(snapshot.status)">
                      {{ statusText(snapshot.status) }}
                    </span>
                  </div>
                  <div class="snapshot-meta">
                    <span v-if="snapshot.duration">{{ formatDuration(snapshot.duration) }}</span>
                    <span v-if="snapshot.totalTokens">{{ snapshot.totalTokens }} tokens</span>
                    <span v-if="snapshot.startTime">{{ formatTime(snapshot.startTime) }}</span>
                  </div>
                  <div v-if="snapshot.errorMessage" class="snapshot-error">{{ snapshot.errorMessage }}</div>
                  <details v-if="snapshot.outputs" class="snapshot-details">
                    <summary>输出结果</summary>
                    <pre>{{ JSON.stringify(snapshot.outputs, null, 2) }}</pre>
                  </details>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { workflowApi } from '@/services/api'

const executions = ref<any[]>([])
const workflows = ref<any[]>([])
const loading = ref(true)
const filterWorkflowId = ref('')
const selectedExecution = ref<any>(null)
const snapshots = ref<any[]>([])
const detailLoading = ref(false)

onMounted(async () => {
  await Promise.all([loadWorkflows(), loadExecutions()])
})

async function loadWorkflows() {
  try {
    workflows.value = await workflowApi.list()
  } catch (e) {
    console.error('Failed to load workflows', e)
  }
}

async function loadExecutions() {
  loading.value = true
  try {
    executions.value = await workflowApi.listExecutions(filterWorkflowId.value || undefined)
  } catch (e) {
    console.error('Failed to load executions', e)
    executions.value = []
  } finally {
    loading.value = false
  }
}

async function showDetail(exec: any) {
  selectedExecution.value = exec
  snapshots.value = []
  detailLoading.value = true
  try {
    const detail = await workflowApi.getExecutionDetail(exec.id)
    snapshots.value = detail.snapshots
  } catch (e) {
    console.error('Failed to load execution detail', e)
  } finally {
    detailLoading.value = false
  }
}

function getWorkflowName(workflowId: string): string {
  const wf = workflows.value.find(w => w.id === workflowId)
  return wf ? wf.name : workflowId
}

function statusClass(status: string): string {
  const map: Record<string, string> = {
    SUCCESS: 'success',
    COMPLETED: 'success',
    FAILED: 'failed',
    ERROR: 'failed',
    RUNNING: 'running',
    PENDING: 'pending',
  }
  return map[status] || 'pending'
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    COMPLETED: '完成',
    FAILED: '失败',
    ERROR: '错误',
    RUNNING: '运行中',
    PENDING: '等待中',
  }
  return map[status] || status
}

function formatTime(ts: number): string {
  if (!ts) return '-'
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${(ms / 60000).toFixed(1)}min`
}
</script>

<style scoped>
.execution-history {
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #1E293B;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filter-select {
  padding: 7px 12px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 13px;
  color: #475569;
  background: #fff;
  outline: none;
  cursor: pointer;
}

.filter-select:focus {
  border-color: #3B82F6;
}

.btn-refresh {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-refresh:hover {
  border-color: #3B82F6;
  color: #3B82F6;
}

/* 列表 */
.execution-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.execution-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #E2E8F0;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}

.execution-card:hover {
  border-color: #93C5FD;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.08);
}

.exec-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 200px;
}

.exec-center {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  justify-content: center;
}

.exec-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.exec-workflow {
  font-size: 14px;
  font-weight: 500;
  color: #1E293B;
}

.exec-id {
  font-family: 'SF Mono', monospace;
  font-size: 12px;
  color: #94A3B8;
  background: #F1F5F9;
  padding: 2px 8px;
  border-radius: 4px;
}

.exec-time {
  font-size: 13px;
  color: #64748B;
}

.exec-duration {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}

.exec-tokens {
  font-size: 12px;
  color: #94A3B8;
}

.exec-arrow {
  flex-shrink: 0;
}

/* 状态 */
.exec-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 12px;
}

.exec-status.sm {
  font-size: 11px;
  padding: 2px 8px;
}

.exec-status .status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.exec-status.success {
  background: #ECFDF5;
  color: #027A48;
}
.exec-status.success .status-dot {
  background: #12B76A;
}

.exec-status.failed {
  background: #FEF3F2;
  color: #B42318;
}
.exec-status.failed .status-dot {
  background: #F04438;
}

.exec-status.running {
  background: #EFF6FF;
  color: #1D4ED8;
}
.exec-status.running .status-dot {
  background: #3B82F6;
  animation: pulse 1.5s infinite;
}

.exec-status.pending {
  background: #F8FAFC;
  color: #475569;
}
.exec-status.pending .status-dot {
  background: #94A3B8;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #94A3B8;
}

.empty-state p {
  font-size: 15px;
  font-weight: 500;
  color: #64748B;
  margin: 16px 0 4px;
}

.empty-state span {
  font-size: 13px;
}

.loading-state {
  text-align: center;
  padding: 40px;
  color: #94A3B8;
  font-size: 14px;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 16px;
  width: 720px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #F1F5F9;
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  cursor: pointer;
  color: #94A3B8;
  padding: 4px;
  border-radius: 6px;
  display: flex;
  align-items: center;
}

.modal-close:hover {
  background: #F1F5F9;
  color: #475569;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

/* 详情 */
.detail-section {
  margin-bottom: 28px;
}

.detail-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: #1E293B;
  margin: 0 0 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 12px;
  color: #94A3B8;
}

.detail-value {
  font-size: 14px;
  color: #1E293B;
  font-weight: 500;
}

.detail-value.mono {
  font-family: 'SF Mono', monospace;
  font-size: 12px;
  word-break: break-all;
}

.error-block {
  margin-top: 16px;
  padding: 12px;
  background: #FEF3F2;
  border: 1px solid #FEE4E2;
  border-radius: 8px;
}

.error-label {
  font-size: 12px;
  color: #B42318;
  font-weight: 500;
  display: block;
  margin-bottom: 6px;
}

.error-block pre {
  font-size: 12px;
  color: #B42318;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 快照时间线 */
.snapshot-timeline {
  position: relative;
  padding-left: 20px;
}

.snapshot-timeline::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 8px;
  bottom: 8px;
  width: 2px;
  background: #E2E8F0;
}

.snapshot-item {
  position: relative;
  margin-bottom: 16px;
}

.snapshot-dot {
  position: absolute;
  left: -20px;
  top: 12px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #fff;
  z-index: 1;
}

.snapshot-dot.success { background: #12B76A; }
.snapshot-dot.failed { background: #F04438; }
.snapshot-dot.running { background: #3B82F6; }
.snapshot-dot.pending { background: #94A3B8; }

.snapshot-card {
  padding: 12px 16px;
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
}

.snapshot-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.snapshot-name {
  font-size: 13px;
  font-weight: 600;
  color: #1E293B;
}

.snapshot-type {
  font-size: 11px;
  color: #64748B;
  background: #EFF6FF;
  padding: 1px 6px;
  border-radius: 4px;
}

.snapshot-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #94A3B8;
}

.snapshot-error {
  margin-top: 6px;
  font-size: 12px;
  color: #B42318;
}

.snapshot-details {
  margin-top: 8px;
}

.snapshot-details summary {
  font-size: 12px;
  color: #3B82F6;
  cursor: pointer;
  font-weight: 500;
}

.snapshot-details pre {
  margin-top: 6px;
  padding: 8px;
  background: #1E293B;
  color: #E2E8F0;
  border-radius: 6px;
  font-size: 11px;
  overflow-x: auto;
  max-height: 200px;
}

.empty-hint {
  font-size: 13px;
  color: #94A3B8;
  text-align: center;
  padding: 20px;
}
</style>
