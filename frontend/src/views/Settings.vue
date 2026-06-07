<template>
  <div class="settings-page">
    <header class="page-header">
      <div class="header-left">
        <h1 class="page-title">系统设置</h1>
        <p class="page-desc">配置数据库连接、模型参数等系统选项</p>
      </div>
    </header>

    <main class="page-content">
      <div class="settings-sections">
        <!-- 数据库配置 -->
        <section class="setting-section">
          <h2 class="section-title">数据库配置</h2>
          <div class="setting-list">
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">MySQL 数据库</span>
                <span class="setting-desc">工作流数据持久化存储</span>
              </div>
              <div class="setting-value">
                <span class="status-badge ready">已连接</span>
                <span class="config-detail">localhost:3306/langgraph4j-engine</span>
              </div>
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">Redis 缓存</span>
                <span class="setting-desc">检查点和状态管理</span>
              </div>
              <div class="setting-value">
                <span class="status-badge ready">已连接</span>
                <span class="config-detail">localhost:6379</span>
              </div>
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">PGVector 向量库</span>
                <span class="setting-desc">知识库向量检索（可选）</span>
              </div>
              <div class="setting-value">
                <span class="status-badge disabled">未启用</span>
              </div>
            </div>
          </div>
        </section>

        <!-- 模型配置 -->
        <section class="setting-section">
          <h2 class="section-title">模型配置</h2>
          <div class="setting-list">
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">LLM 模型</span>
                <span class="setting-desc">大语言模型用于对话推理</span>
              </div>
              <div class="setting-value">
                <span class="config-detail">qwen2.5:3b @ Ollama</span>
              </div>
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">Embedding 模型</span>
                <span class="setting-desc">文本向量化用于知识检索</span>
              </div>
              <div class="setting-value">
                <span class="config-detail">nomic-embed-text @ Ollama</span>
              </div>
            </div>
          </div>
        </section>

        <!-- 系统信息 -->
        <section class="setting-section">
          <h2 class="section-title">系统信息</h2>
          <div class="setting-list">
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">版本</span>
              </div>
              <div class="setting-value">
                <span class="config-detail">v1.0.0</span>
              </div>
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">后端版本</span>
              </div>
              <div class="setting-value">
                <span class="config-detail">Java 17 + Vert.x 4.5.1</span>
              </div>
            </div>
            <div class="setting-item">
              <div class="setting-info">
                <span class="setting-name">前端版本</span>
              </div>
              <div class="setting-value">
                <span class="config-detail">Vue 3 + TypeScript</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}

.page-desc {
  font-size: 13px;
  color: #6b7280;
  margin-top: 4px;
}

.settings-sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.setting-section {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 16px;
}

.setting-list {
  display: flex;
  flex-direction: column;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid #f3f4f6;
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.setting-name {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.setting-desc {
  font-size: 12px;
  color: #9ca3af;
}

.setting-value {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-badge {
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.ready {
  background: #dcfce7;
  color: #166534;
}

.status-badge.disabled {
  background: #f3f4f6;
  color: #9ca3af;
}

.config-detail {
  font-size: 13px;
  color: #6b7280;
  font-family: monospace;
}
</style>
