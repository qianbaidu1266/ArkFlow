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
        v-for="exec in pagedExecutions"
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

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination-bar">
      <div class="pagination-info">
        共 {{ executions.length }} 条记录，第 {{ currentPage }} / {{ totalPages }} 页
      </div>
      <div class="pagination-controls">
        <button
          class="page-btn page-nav"
          :disabled="currentPage === 1"
          @click="goPage(currentPage - 1)"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>
          上一页
        </button>

        <template v-for="page in visiblePages" :key="page">
          <button
            v-if="page === '...'"
            class="page-btn page-ellipsis"
            disabled
          >...</button>
          <button
            v-else
            class="page-btn"
            :class="{ active: page === currentPage }"
            @click="goPage(page as number)"
          >{{ page }}</button>
        </template>

        <button
          class="page-btn page-nav"
          :disabled="currentPage === totalPages"
          @click="goPage(currentPage + 1)"
        >
          下一页
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18l6-6-6-6"/></svg>
        </button>
      </div>
      <select v-model.number="pageSize" @change="onPageSizeChange" class="page-size-select">
        <option :value="5">5 条/页</option>
        <option :value="10">10 条/页</option>
        <option :value="15">15 条/页</option>
      </select>
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
          <!-- 执行概览 -->
          <div class="exec-overview">
            <div class="overview-main">
              <span class="exec-status" :class="statusClass(selectedExecution.status)">
                <span class="status-dot"></span>
                {{ statusText(selectedExecution.status) }}
              </span>
              <span class="overview-workflow">{{ getWorkflowName(selectedExecution.workflowId) }}</span>
            </div>
            <div class="overview-meta">
              <div class="meta-item">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#94A3B8" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
                <span>{{ formatTime(selectedExecution.startTime) }}</span>
                <template v-if="selectedExecution.duration">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#94A3B8" stroke-width="2"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
                  <span>{{ formatDuration(selectedExecution.duration) }}</span>
                </template>
                <template v-if="snapshots.length > 0">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#94A3B8" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
                  <span>{{ snapshots.length }} 个节点</span>
                </template>
              </div>
            </div>
          </div>

          <!-- 错误信息 -->
          <div v-if="selectedExecution.error" class="error-block">
            <div class="error-header">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#F04438" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
              <span>执行失败</span>
            </div>
            <pre>{{ selectedExecution.error }}</pre>
          </div>

          <!-- 节点执行流水线 -->
          <div class="detail-section">
            <div class="section-header-row">
              <h4>节点执行记录</h4>
              <span class="node-count">{{ snapshots.length }} 个节点</span>
            </div>

            <div v-if="detailLoading" class="loading-state">
              <div class="spinner-sm"></div>
              加载节点数据...
            </div>
            <div v-else-if="detailError" class="empty-hint error-state">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#F04438" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
              <p>加载节点数据失败</p>
              <span class="error-msg">{{ detailError }}</span>
              <button class="btn-retry" @click="showDetail(selectedExecution)">重试</button>
            </div>
            <div v-else-if="snapshots.length === 0" class="empty-hint">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#CBD5E1" stroke-width="1.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9h6M9 13h6M9 17h4"/></svg>
              暂无节点执行记录
            </div>
            <div v-else class="snapshot-timeline">
              <div v-for="(snapshot, index) in snapshots" :key="snapshot.id" class="snapshot-item">
                <!-- 连接线 -->
                <div v-if="index < snapshots.length - 1" class="snapshot-line" :class="'line-' + statusClass(snapshot.status)"></div>

                <!-- 节点圆点 -->
                <div class="snapshot-dot" :class="statusClass(snapshot.status)">
                  <svg v-if="snapshot.nodeType === 'start' || snapshot.nodeType === 'end'" width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="8"/></svg>
                  <svg v-else-if="snapshot.nodeType === 'agent'" width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>
                  <svg v-else-if="snapshot.nodeType === 'code'" width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"/></svg>
                  <svg v-else width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="5"/></svg>
                </div>

                <!-- 节点卡片 -->
                <div class="snapshot-card" :class="{ 'card-error': snapshot.errorMessage }">
                  <div class="snapshot-header">
                    <div class="snapshot-title-row">
                      <span class="snapshot-name">{{ snapshot.nodeName }}</span>
                      <span class="snapshot-type-badge" :class="'badge-' + snapshot.nodeType">{{ nodeTypeLabel(snapshot.nodeType) }}</span>
                      <span class="exec-status sm" :class="statusClass(snapshot.status)">
                        {{ statusText(snapshot.status) }}
                      </span>
                    </div>
                    <div class="snapshot-stats">
                      <span v-if="snapshot.duration" class="stat-tag">
                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
                        {{ formatDuration(snapshot.duration) }}
                      </span>
                      <span v-if="snapshot.totalTokens > 0" class="stat-tag">
                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
                        {{ snapshot.totalTokens }}
                      </span>
                      <span v-if="snapshot.startTime" class="stat-tag time">{{ formatTime(snapshot.startTime) }}</span>
                    </div>
                  </div>

                  <!-- 输入 -->
                  <div v-if="snapshot.inputs && Object.keys(snapshot.inputs).length > 0" class="snapshot-data-block">
                    <div class="data-label">输入</div>
                    <div class="data-content">{{ formatSnapshotData(snapshot.inputs) }}</div>
                  </div>

                  <!-- 输出 -->
                  <div v-if="snapshot.outputs && Object.keys(snapshot.outputs).length > 0" class="snapshot-data-block output">
                    <div class="data-label">输出</div>
                    <div class="data-content">{{ formatSnapshotData(snapshot.outputs) }}</div>
                  </div>

                  <!-- 错误 -->
                  <div v-if="snapshot.errorMessage" class="snapshot-error-inline">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#F04438" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                    {{ snapshot.errorMessage }}
                  </div>

                  <!-- 展开详情 -->
                  <details v-if="(snapshot.outputs && Object.keys(snapshot.outputs).length > 0) || (snapshot.inputs && Object.keys(snapshot.inputs).length > 0)" class="snapshot-details">
                    <summary>查看原始 JSON</summary>
                    <pre><code>{{ JSON.stringify({ inputs: snapshot.inputs, outputs: snapshot.outputs }, null, 2) }}</code></pre>
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
import { ref, computed, onMounted } from 'vue'
import { workflowApi } from '@/services/api'

const executions = ref<any[]>([])
const workflows = ref<any[]>([])
const loading = ref(true)
const filterWorkflowId = ref('')
const selectedExecution = ref<any>(null)
const snapshots = ref<any[]>([])
const detailLoading = ref(false)
const detailError = ref<string | null>(null)

// 分页
const currentPage = ref(1)
const pageSize = ref(10)

const totalPages = computed(() => Math.max(1, Math.ceil(executions.value.length / pageSize.value)))

const pagedExecutions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return executions.value.slice(start, start + pageSize.value)
})

// 页码显示（带省略号）
const visiblePages = computed<(number | string)[]>(() => {
  const total = totalPages.value
  const current = currentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages: (number | string)[] = [1]
  if (current > 3) pages.push('...')
  for (let i = Math.max(2, current - 1); i <= Math.min(total - 1, current + 1); i++) {
    pages.push(i)
  }
  if (current < total - 2) pages.push('...')
  pages.push(total)
  return pages
})

function goPage(page: number) {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function onPageSizeChange() {
  currentPage.value = 1
}

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
  currentPage.value = 1
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
  detailError.value = null
  detailLoading.value = true
  try {
    // 只调用 snapshots 接口（/executions/:id 单独接口可能 404）
    const snapshotsRes = await workflowApi.getExecutionSnapshots(exec.id)
    snapshots.value = snapshotsRes || []
  } catch (e) {
    console.error('[ExecutionHistory] Failed to load execution detail', e)
    detailError.value = e instanceof Error ? e.message : String(e)
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

function nodeTypeLabel(type: string): string {
  const map: Record<string, string> = { start: '开始', end: '结束', agent: 'Agent', code: '代码', tool: '工具', condition: '条件', llm: 'LLM' }
  return map[type] || type
}

function formatSnapshotData(data: any): string {
  if (!data || typeof data !== 'object') return String(data ?? '')
  // 截断长文本
  const str = JSON.stringify(data, null, 2)
  return str.length > 200 ? str.substring(0, 200) + '...' : str
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

.empty-hint.error-state p {
  margin: 10px 0 4px;
  font-weight: 500;
  color: #B42318;
}

.error-msg {
  display: block;
  font-size: 12px;
  color: #F04438;
  word-break: break-all;
  max-width: 400px;
  margin: 0 auto 12px;
}

.btn-retry {
  padding: 6px 16px;
  border: 1px solid #FECACA;
  border-radius: 8px;
  background: #FEF2F2;
  color: #B42318;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-retry:hover {
  background: #FEE2E2;
  border-color: #FCA5A5;
}

/* 分页 */
.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0 8px;
  gap: 16px;
  flex-wrap: wrap;
}

.pagination-info {
  font-size: 13px;
  color: #64748B;
  white-space: nowrap;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 4px;
}

.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.page-btn:hover:not(:disabled):not(.active) {
  border-color: #93C5FD;
  background: #F0F7FF;
  color: #1D4ED8;
}

.page-btn.active {
  background: #3B82F6;
  border-color: #3B82F6;
  color: #fff;
  font-weight: 600;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-nav {
  gap: 4px;
  padding: 0 12px;
  font-size: 13px;
}

.page-ellipsis {
  border: none;
  background: transparent;
  color: #94A3B8;
  min-width: auto;
  width: 28px;
  height: 28px;
  font-size: 14px;
}

.page-size-select {
  padding: 6px 10px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 13px;
  color: #475569;
  background: #fff;
  outline: none;
  cursor: pointer;
}

.page-size-select:focus {
  border-color: #3B82F6;
}

/* 执行概览 */
.exec-overview {
  background: linear-gradient(135deg, #F0F7FF 0%, #F8FAFC 100%);
  border: 1px solid #DBEAFE;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 20px;
}

.overview-main {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.overview-workflow {
  font-size: 15px;
  font-weight: 600;
  color: #1E293B;
}

.overview-meta .meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #64748B;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #B42318;
  margin-bottom: 8px;
}

/* 节点记录标题行 */
.section-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-header-row h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #1E293B;
}

.node-count {
  font-size: 12px;
  color: #94A3B8;
  background: #F1F5F9;
  padding: 2px 10px;
  border-radius: 10px;
}

/* 连接线 */
.snapshot-line {
  position: absolute;
  left: 5px;
  top: 24px;
  bottom: -4px;
  width: 2px;
}
.snapshot-line.line-success { background: linear-gradient(to bottom, #12B76A, #93D9A9); }
.snapshot-line.line-failed { background: linear-gradient(to bottom, #F04438, #FCA5A5); }
.snapshot-line.line-running { background: linear-gradient(to bottom, #3B82F6, #93C5FD); }
.snapshot-line.line-pending { background: #E2E8F0; }

/* 节点圆点增强 */
.snapshot-dot {
  display: flex;
  align-items: center;
  justify-content: center;
}
.snapshot-dot.success svg { color: #fff; }
.snapshot-dot.failed svg { color: #fff; }
.snapshot-dot.running svg { color: #fff; }
.snapshot-dot.pending svg { color: #94A3B8; }

/* 卡片增强 */
.snapshot-card.card-error {
  border-color: #FECACA;
  background: #FEF2F2;
}

.snapshot-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.snapshot-type-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 1px 8px;
  border-radius: 4px;
}
.badge-start, .badge-end { background: #EDE9FE; color: #7C3AED; }
.badge-agent { background: #EFF6FF; color: #2563EB; }
.badge-code { background: #ECFDF5; color: #059669; }
.badge-tool { background: #FFF7ED; color: #EA580C; }
.badge-condition { background: #FDF4FF; color: #C026D3; }
.badge-llm { background: #FFF1F2; color: #E11D48; }

/* 统计标签 */
.snapshot-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.stat-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #64748B;
  background: #F1F5F9;
  padding: 2px 7px;
  border-radius: 4px;
}
.stat-tag.time { color: #94A3B8; font-family: 'SF Mono', monospace; font-size: 10px; }

/* 数据块展示 */
.snapshot-data-block {
  margin-top: 10px;
  padding: 10px 12px;
  background: #F8FAFC;
  border-radius: 6px;
  border-left: 3px solid #CBD5E1;
}
.snapshot-data-block.output {
  border-left-color: #3B82F6;
  background: #F0F7FF;
}

.data-label {
  font-size: 11px;
  font-weight: 600;
  color: #94A3B8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.data-content {
  font-size: 12px;
  color: #334155;
  line-height: 1.5;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 80px;
  overflow-y: auto;
  font-family: 'SF Mono', Monaco, monospace;
}

/* 内联错误 */
.snapshot-error-inline {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 12px;
  background: #FEF2F2;
  border: 1px solid #FECACA;
  border-radius: 6px;
  font-size: 12px;
  color: #B42318;
  word-break: break-all;
}
.snapshot-error-inline svg {
  flex-shrink: 0;
  margin-top: 1px;
}

/* 加载动画 */
.spinner-sm {
  width: 18px;
  height: 18px;
  border: 2px solid #E2E8F0;
  border-top-color: #3B82F6;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  margin: 0 auto 8px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
