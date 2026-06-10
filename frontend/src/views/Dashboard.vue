<template>
  <div class="dashboard">
    <!-- Hero 区域 -->
    <section class="hero-section">
      <div class="hero-card">
        <div class="hero-content">
          <div class="hero-badge">基于 LangGraph</div>
          <h1 class="hero-title">
            可视化流程编排，<br>
            定义你的 AI 智能体。
          </h1>
          <p class="hero-desc">
            基于 LangGraph 的工作流引擎——可视化编排流程节点，支持自定义智能体、<br>
            RAG 知识库检索、条件分支、代码执行等，拖拽连线构建复杂 AI 工作流。
          </p>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat-card stat-blue">
          <div class="stat-left">
            <div class="stat-icon-bg">
              <WorkflowIcon />
            </div>
            <div class="stat-info">
              <div class="stat-label">工作流</div>
              <div class="stat-value">{{ stats.workflowCount }}</div>
            </div>
          </div>
          <div class="stat-arrow">→</div>
        </div>
        <div class="stat-card stat-purple">
          <div class="stat-left">
            <div class="stat-icon-bg">
              <KnowledgeIcon />
            </div>
            <div class="stat-info">
              <div class="stat-label">知识库</div>
              <div class="stat-value">{{ stats.knowledgeCount }}</div>
            </div>
          </div>
          <div class="stat-arrow">→</div>
        </div>
        <div class="stat-card stat-green">
          <div class="stat-left">
            <div class="stat-icon-bg">
              <RunIcon />
            </div>
            <div class="stat-info">
              <div class="stat-label">运行次数</div>
              <div class="stat-value">{{ stats.runCount }}</div>
            </div>
          </div>
          <div class="stat-arrow">→</div>
        </div>
      </div>
    </section>

    <!-- 快速开始 -->
    <section class="quick-start">
      <h2 class="section-title">
        <span class="section-icon">⚡</span>
        快速开始
      </h2>
      <p class="section-subtitle">按以下步骤快速上手。</p>
      <div class="steps">
        <div class="step-item">
          <div class="step-number">01</div>
          <h3 class="step-title">创建工作流</h3>
          <p class="step-desc">选择节点类型，配置模型参数和提示词。</p>
        </div>
        <div class="step-divider"></div>
        <div class="step-item">
          <div class="step-number">02</div>
          <h3 class="step-title">连线编排</h3>
          <p class="step-desc">拖拽连线，定义数据流向和条件分支。</p>
        </div>
        <div class="step-divider"></div>
        <div class="step-item">
          <div class="step-number">03</div>
          <h3 class="step-title">运行调试</h3>
          <p class="step-desc">一键运行，实时查看节点执行状态和结果。</p>
        </div>
      </div>
    </section>

    <!-- 系统状态 -->
    <section class="system-status">
      <div class="status-header">
        <h2 class="section-title">
          <span class="section-icon">◉</span>
          系统状态
        </h2>
        <router-link to="/settings" class="settings-link">
          前往设置 →
        </router-link>
      </div>
      <p class="section-subtitle">运行工作流前，检查配置是否就绪。</p>
      <div class="status-list">
        <div class="status-item">
          <div class="status-left">
            <span class="status-dot" :class="services.mysql ? 'ready' : 'error'"></span>
            <span class="status-name">MySQL 数据库</span>
          </div>
          <span class="status-text" :class="services.mysql ? 'ready' : 'error'">
            {{ services.mysql ? '已连接' : '未连接' }}
          </span>
        </div>
        <div class="status-item">
          <div class="status-left">
            <span class="status-dot" :class="services.redis ? 'ready' : 'error'"></span>
            <span class="status-name">Redis 缓存</span>
          </div>
          <span class="status-text" :class="services.redis ? 'ready' : 'error'">
            {{ services.redis ? '已连接' : '未连接' }}
          </span>
        </div>
        <div class="status-item">
          <div class="status-left">
            <span class="status-dot" :class="services.llm ? 'ready' : 'error'"></span>
            <span class="status-name">LLM 模型</span>
          </div>
          <span class="status-text" :class="services.llm ? 'ready' : 'error'">
            {{ services.llm ? '已配置' : '未配置' }}
          </span>
        </div>
        <div class="status-item">
          <div class="status-left">
            <span class="status-dot" :class="services.pgvector ? 'ready' : 'warning'"></span>
            <span class="status-name">向量数据库</span>
          </div>
          <span class="status-text" :class="services.pgvector ? 'ready' : 'warning'">
            {{ services.pgvector ? '已连接' : '未启用' }}
          </span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useWorkflowStore } from '@/stores/workflow'
import { knowledgeApi, workflowApi } from '@/services/api'

const workflowStore = useWorkflowStore()

const stats = ref({
  workflowCount: 0,
  knowledgeCount: 0,
  runCount: 0,
})

const services = ref({
  mysql: true,
  redis: true,
  llm: true,
  pgvector: false,
})

onMounted(async () => {
  await workflowStore.fetchWorkflows()
  stats.value.workflowCount = workflowStore.workflows.length

  // 获取知识库数量
  try {
    const knowledgeBases = await knowledgeApi.list()
    stats.value.knowledgeCount = knowledgeBases.length
  } catch (e) {
    console.error('Failed to fetch knowledge bases', e)
  }

  // 获取运行次数
  try {
    const executions = await workflowApi.listExecutions()
    stats.value.runCount = executions.length
  } catch (e) {
    console.error('Failed to fetch executions', e)
  }
})
</script>

<script lang="ts">
// 内联 SVG 图标组件
const WorkflowIcon = {
  render() {
    return h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '20', height: '20' }, [
      h('rect', { x: '3', y: '3', width: '7', height: '7', rx: '1' }),
      h('rect', { x: '14', y: '3', width: '7', height: '7', rx: '1' }),
      h('rect', { x: '14', y: '14', width: '7', height: '7', rx: '1' }),
      h('rect', { x: '3', y: '14', width: '7', height: '7', rx: '1' }),
    ])
  }
}

const KnowledgeIcon = {
  render() {
    return h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '20', height: '20' }, [
      h('path', { d: 'M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20' }),
    ])
  }
}

const RunIcon = {
  render() {
    return h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2', width: '20', height: '20' }, [
      h('circle', { cx: '12', cy: '12', r: '10' }),
      h('polyline', { points: '12 6 12 12 16 14' }),
    ])
  }
}

import { h } from 'vue'
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
}

/* Hero 区域 - 左右布局 */
.hero-section {
  margin-bottom: 32px;
  display: flex;
  gap: 20px;
}

.hero-card {
  flex: 0 0 70%;
  background: linear-gradient(135deg, #0F172A 0%, #1E293B 50%, #334155 100%);
  border-radius: 16px;
  padding: 40px;
  display: flex;
  align-items: center;
}

.hero-content {
  flex: 1;
}

.hero-badge {
  display: inline-block;
  padding: 4px 12px;
  background: rgba(56, 189, 248, 0.15);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 12px;
  font-size: 12px;
  color: #38BDF8;
  margin-bottom: 16px;
}

.hero-title {
  font-size: 32px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.3;
  margin-bottom: 12px;
}

.hero-desc {
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.6;
}

/* 右侧统计卡片 - 垂直排列 */
.hero-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.stat-card {
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.stat-blue {
  background: linear-gradient(135deg, #DBEAFE 0%, #EFF6FF 100%);
  border: 1px solid #BFDBFE;
}

.stat-purple {
  background: linear-gradient(135deg, #E0E7FF 0%, #EEF2FF 100%);
  border: 1px solid #C7D2FE;
}

.stat-green {
  background: linear-gradient(135deg, #D1FAE5 0%, #ECFDF5 100%);
  border: 1px solid #A7F3D0;
}

.stat-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon-bg {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
}

.stat-blue .stat-icon-bg {
  background: #3B82F6;
  color: #ffffff;
}

.stat-purple .stat-icon-bg {
  background: #6366F1;
  color: #ffffff;
}

.stat-green .stat-icon-bg {
  background: #10B981;
  color: #ffffff;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 13px;
  font-weight: 500;
  color: #6B7280;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1F2937;
}

.stat-arrow {
  font-size: 18px;
  color: #9CA3AF;
  font-weight: 300;
}

/* 快速开始 */
.quick-start {
  background: #ffffff;
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.section-icon {
  font-size: 18px;
}

.section-subtitle {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 32px;
}

.steps {
  display: flex;
  align-items: stretch;
  gap: 0;
}

.step-item {
  flex: 1;
  padding: 0 32px;
  position: relative;
}

.step-item:first-child {
  padding-left: 0;
}

.step-item:last-child {
  padding-right: 0;
}

/* 左侧彩色竖线 */
.step-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 2px;
}

.step-item:first-child::before {
  display: none;
}

.step-item:nth-child(1)::before {
  background: #93C5FD;
}

.step-item:nth-child(3)::before {
  background: #A5B4FC;
}

.step-item:nth-child(5)::before {
  background: #86EFAC;
}

.step-number {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 12px;
  position: relative;
  display: block;
}

/* 数字下方横线 - 贯穿整个步骤宽度 */
.step-number::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 4px;
  height: 1px;
  background: #e5e7eb;
}

.step-item:nth-child(1) .step-number {
  color: #93C5FD;
}

.step-item:nth-child(1) .step-number::after {
  background: #93C5FD;
}

.step-item:nth-child(3) .step-number {
  color: #A5B4FC;
}

.step-item:nth-child(3) .step-number::after {
  background: #A5B4FC;
}

.step-item:nth-child(5) .step-number {
  color: #86EFAC;
}

.step-item:nth-child(5) .step-number::after {
  background: #86EFAC;
}

.step-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 6px;
}

.step-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.step-divider {
  width: 1px;
  background: #e5e7eb;
  margin: 8px 0;
}

/* 系统状态 */
.system-status {
  background: #ffffff;
  border-radius: 16px;
  padding: 32px;
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.settings-link {
  font-size: 13px;
  color: #6b7280;
  text-decoration: none;
}

.settings-link:hover {
  color: #1f2937;
}

.status-list {
  margin-top: 20px;
}

.status-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}

.status-item:last-child {
  border-bottom: none;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.ready {
  background: #22c55e;
}

.status-dot.error {
  background: #ef4444;
}

.status-dot.warning {
  background: #f59e0b;
}

.status-name {
  font-size: 14px;
  color: #374151;
}

.status-text {
  font-size: 13px;
  font-weight: 500;
}

.status-text.ready {
  color: #22c55e;
}

.status-text.error {
  color: #ef4444;
}

.status-text.warning {
  color: #f59e0b;
}
</style>
