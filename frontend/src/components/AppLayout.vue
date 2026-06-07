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
        <span class="brand-text">LangGraph4J</span>
      </div>

      <nav class="nav-tabs">
        <router-link
          v-for="tab in tabs"
          :key="tab.path"
          :to="tab.path"
          class="nav-tab"
          :class="{ active: isActive(tab.path) }"
        >
          <svg class="tab-icon" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" v-html="tab.svgPath" />
          <span class="tab-label">{{ tab.label }}</span>
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

const route = useRoute()

const tabs = [
  {
    path: '/',
    label: '总览',
    svgPath: '<rect x="2" y="2" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.5"/><rect x="11" y="2" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.5"/><rect x="2" y="11" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.5"/><rect x="11" y="11" width="7" height="7" rx="1.5" stroke="currentColor" stroke-width="1.5"/>'
  },
  {
    path: '/workflows',
    label: '工作流',
    svgPath: '<circle cx="5" cy="10" r="2.5" stroke="currentColor" stroke-width="1.5"/><circle cx="15" cy="5" r="2.5" stroke="currentColor" stroke-width="1.5"/><circle cx="15" cy="15" r="2.5" stroke="currentColor" stroke-width="1.5"/><path d="M7.5 10H12.5M12.5 5L7.5 8.5M12.5 15L7.5 11.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>'
  },
  {
    path: '/history',
    label: '运行历史',
    svgPath: '<circle cx="10" cy="10" r="7" stroke="currentColor" stroke-width="1.5"/><path d="M10 6V10L13 12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>'
  },
  {
    path: '/knowledge',
    label: '知识库',
    svgPath: '<path d="M3 4.5C3 3.67 3.67 3 4.5 3H8L10 5H15.5C16.33 5 17 5.67 17 6.5V15.5C17 16.33 16.33 17 15.5 17H4.5C3.67 17 3 16.33 3 15.5V4.5Z" stroke="currentColor" stroke-width="1.5"/><path d="M7 10H13M7 13H11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>'
  },
  {
    path: '/models',
    label: '模型',
    svgPath: '<path d="M10 2L17.5 6V14L10 18L2.5 14V6L10 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/><path d="M10 10L17.5 6M10 10V18M10 10L2.5 6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>'
  },
  {
    path: '/settings',
    label: '设置',
    svgPath: '<circle cx="10" cy="10" r="2.5" stroke="currentColor" stroke-width="1.5"/><path d="M10 2V4M10 16V18M2 10H4M16 10H18M4.22 4.22L5.64 5.64M14.36 14.36L15.78 15.78M15.78 4.22L14.36 5.64M5.64 14.36L4.22 15.78" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>'
  },
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
  height: 56px;
  background: linear-gradient(135deg, #EFF6FF 0%, #F0F7FF 50%, #F8FAFC 100%);
  border-bottom: 1px solid #DBEAFE;
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

/* Tab 导航 */
.nav-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 14px;
  border-radius: 8px;
  color: #64748B;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.15s ease;
}

.nav-tab:hover {
  background: rgba(59, 130, 246, 0.08);
  color: #3B82F6;
}

.nav-tab.active {
  background: #3B82F6;
  color: #ffffff;
  box-shadow: 0 1px 3px rgba(59, 130, 246, 0.3);
}

.nav-tab.active:hover {
  background: #2563EB;
}

.tab-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
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
</style>
