<template>
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <header class="top-nav">
      <div class="nav-brand">
        <svg class="brand-svg" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="32" height="32" rx="8" fill="#3B82F6"/>
          <path d="M8 10L16 6L24 10V22L16 26L8 22V10Z" stroke="white" stroke-width="1.5" fill="none"/>
          <circle cx="16" cy="16" r="3" fill="white"/>
          <path d="M16 13V8M16 24V19M19 16H24M8 16H13" stroke="white" stroke-width="1.2" stroke-linecap="round"/>
        </svg>
        <span class="brand-text">ArkFlow</span>
      </div>

      <nav class="topnav">
        <router-link
          v-for="tab in tabs"
          :key="tab.path"
          :to="tab.path"
          class="topnav-item"
          :class="{ active: isActive(tab.path) }"
        >
          <component :is="tab.icon" :size="16" />
          <span>{{ tab.label }}</span>
        </router-link>
      </nav>

      <div class="nav-actions">
        <span class="status-badge" :class="systemStatus">
          <span class="status-dot"></span>
          {{ systemStatusText }}
        </span>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { LayoutDashboard, GitBranch, History, BookOpen, Box, Settings } from 'lucide-vue-next'

const route = useRoute()

const tabs = [
  { path: '/', label: '总览', icon: LayoutDashboard },
  { path: '/workflows', label: '工作流', icon: GitBranch },
  { path: '/history', label: '运行历史', icon: History },
  { path: '/knowledge', label: '知识库', icon: BookOpen },
  { path: '/models', label: '模型', icon: Box },
  { path: '/settings', label: '设置', icon: Settings },
]

function isActive(path: string): boolean {
  if (path === '/') {
    return route.path === '/'
  }
  return route.path.startsWith(path)
}

const systemStatus = computed(() => 'ready')
const systemStatusText = computed(() => '系统就绪')
</script>

<style scoped>
.app-layout {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f7;
}

/* 顶部导航栏 */
.top-nav {
  height: 80px;
  background: #f5f5f7;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  flex-shrink: 0;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-right: 36px;
  flex-shrink: 0;
}

.brand-svg {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.brand-text {
  font-size: 16px;
  font-weight: 700;
  color: #1E40AF;
  letter-spacing: -0.3px;
}

/* 胶囊导航 */
.topnav {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px;
  border: 1px solid rgba(226, 232, 240, 1);
  border-radius: 999px;
  background: rgba(241, 245, 249, 0.95);
}

.topnav-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 88px;
  padding: 8px 14px;
  border-radius: 999px;
  background: transparent;
  color: #64748b;
  text-decoration: none;
  font-size: 13px;
  font-weight: 600;
  transition: 0.2s ease;
  cursor: pointer;
  border: none;
}

.topnav-item:hover {
  color: #0f172a;
  background: rgba(255, 255, 255, 0.9);
}

.topnav-item.active {
  background: #3B82F6;
  color: #fff;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.topnav-item.active:hover {
  background: #2563EB;
}

/* 右侧操作区 */
.nav-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  margin-left: 36px;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  background: #ecfdf3;
  color: #027a48;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #12b76a;
}

/* 主内容区 */
.main-content {
  flex: 1;
  overflow: auto;
  padding: 24px;
}

/* 响应式 */
@media (max-width: 768px) {
  .topnav {
    overflow: auto;
  }
  .topnav-item {
    min-width: 72px;
    padding: 6px 10px;
    font-size: 12px;
  }
}
</style>
