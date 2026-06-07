<template>
  <div class="knowledge-page">
    <header class="page-header">
      <div class="header-left">
        <h1 class="page-title">知识库</h1>
        <p class="page-desc">管理 RAG 知识库，为工作流提供检索能力</p>
      </div>
      <button class="btn btn-primary" @click="showCreate = true">
        + 新建知识库
      </button>
    </header>

    <main class="page-content">
      <div class="empty-state">
        <div class="empty-icon">📚</div>
        <div class="empty-title">暂无知识库</div>
        <div class="empty-desc">
          知识库功能需要配置 PGVector 向量数据库<br>
          请在设置中配置后使用
        </div>
        <router-link to="/settings" class="btn btn-secondary">
          前往设置
        </router-link>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const showCreate = ref(false)
</script>

<style scoped>
.knowledge-page {
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
  background: #1a1a2e;
  color: #ffffff;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.empty-state {
  background: #ffffff;
  border-radius: 16px;
  padding: 80px 40px;
  text-align: center;
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
  line-height: 1.6;
  margin-bottom: 24px;
}
</style>
