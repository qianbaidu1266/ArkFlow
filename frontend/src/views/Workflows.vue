<template>
  <div class="workflows-page">
    <header class="page-header">
      <div class="header-left">
        <h1 class="page-title">工作流</h1>
        <p class="page-desc">管理和编排 AI 工作流</p>
      </div>
      <router-link to="/editor" class="btn btn-primary">
        + 新建工作流
      </router-link>
    </header>

    <main class="page-content">
      <div v-if="workflowStore.isLoading" class="loading">
        加载中...
      </div>

      <div v-else-if="workflowStore.workflows.length === 0" class="empty-state">
        <div class="empty-icon">📋</div>
        <div class="empty-title">暂无工作流</div>
        <div class="empty-desc">创建您的第一个 AI 工作流</div>
        <router-link to="/editor" class="btn btn-primary">
          创建工作流
        </router-link>
      </div>

      <div v-else class="workflow-grid">
        <div
          v-for="workflow in workflowStore.workflows"
          :key="workflow.id"
          class="workflow-card"
        >
          <div class="card-header">
            <h3 class="workflow-name">{{ workflow.name }}</h3>
            <div class="workflow-actions">
              <router-link
                :to="`/editor/${workflow.id}`"
                class="action-btn"
                title="编辑"
              >
                ✎
              </router-link>
              <button
                class="action-btn delete"
                title="删除"
                @click="deleteWorkflow(workflow.id)"
              >
                🗑
              </button>
            </div>
          </div>

          <div class="card-body">
            <div class="workflow-meta">
              <span class="meta-item">
                {{ workflow.nodeCount || 0 }} 个节点
              </span>
              <span class="meta-item">
                {{ workflow.edgeCount || 0 }} 条连接
              </span>
            </div>
            <div class="workflow-desc" v-if="workflow.description">
              {{ workflow.description }}
            </div>
          </div>

          <div class="card-footer">
            <span class="update-time">
              更新于 {{ formatTime(workflow.updatedAt) }}
            </span>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useWorkflowStore } from '@/stores/workflow'

const workflowStore = useWorkflowStore()

onMounted(() => {
  workflowStore.fetchWorkflows()
})

function deleteWorkflow(id: string) {
  if (confirm('确定要删除这个工作流吗？')) {
    workflowStore.delete(id)
  }
}

function formatTime(timestamp?: number): string {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.workflows-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}

.page-desc {
  font-size: 13px;
  color: #6b7280;
}

.btn {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.btn-primary {
  background: #3B82F6;
  color: #ffffff;
}

.btn-primary:hover {
  background: #2563EB;
}

.page-content {
  min-height: 400px;
}

.loading {
  text-align: center;
  padding: 64px;
  color: #9ca3af;
  background: #ffffff;
  border-radius: 16px;
}

.empty-state {
  text-align: center;
  padding: 80px 40px;
  background: #ffffff;
  border-radius: 16px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 24px;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.workflow-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 20px;
  transition: box-shadow 0.2s;
}

.workflow-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.workflow-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.workflow-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  text-decoration: none;
  cursor: pointer;
  border: none;
  background: transparent;
  font-size: 14px;
}

.action-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.action-btn.delete:hover {
  background: #fee2e2;
  color: #dc2626;
}

.card-body {
  margin-bottom: 12px;
}

.workflow-meta {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
}

.meta-item {
  font-size: 12px;
  color: #6b7280;
  padding: 2px 8px;
  background: #f3f4f6;
  border-radius: 4px;
}

.workflow-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.card-footer {
  border-top: 1px solid #f3f4f6;
  padding-top: 12px;
}

.update-time {
  font-size: 12px;
  color: #9ca3af;
}
</style>
