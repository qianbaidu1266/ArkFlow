<template>
  <div class="workflows-page">
    <header class="page-header">
      <div class="header-left">
        <router-link to="/" class="back-link">← 返回</router-link>
        <h1 class="page-title">工作流列表</h1>
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
  min-height: 100vh;
  background: #f8fafc;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-link {
  color: #64748b;
  text-decoration: none;
  font-size: 14px;
  
  &:hover {
    color: #3b82f6;
  }
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
}

.btn {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-primary {
  background: #3b82f6;
  color: white;
  
  &:hover {
    background: #2563eb;
  }
}

.page-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px;
}

.loading {
  text-align: center;
  padding: 64px;
  color: #94a3b8;
}

.empty-state {
  text-align: center;
  padding: 96px 32px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-title {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 14px;
  color: #94a3b8;
  margin-bottom: 24px;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.workflow-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.2s;
  overflow: hidden;
  
  &:hover {
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;
}

.workflow-name {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  text-decoration: none;
  color: #64748b;
  
  &:hover {
    background: #f1f5f9;
    color: #3b82f6;
  }
  
  &.delete:hover {
    background: #fee2e2;
    color: #ef4444;
  }
}

.card-body {
  padding: 16px 20px;
}

.workflow-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.meta-item {
  font-size: 13px;
  color: #64748b;
  background: #f8fafc;
  padding: 4px 10px;
  border-radius: 4px;
}

.workflow-desc {
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-footer {
  padding: 12px 20px;
  background: #f8fafc;
  border-top: 1px solid #f1f5f9;
}

.update-time {
  font-size: 12px;
  color: #94a3b8;
}
</style>
