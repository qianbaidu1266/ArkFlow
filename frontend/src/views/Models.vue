<template>
  <div class="models-page">
    <header class="page-header">
      <div class="header-left">
        <h1 class="page-title">模型配置</h1>
        <p class="page-desc">管理 LLM 和 Embedding 模型连接</p>
      </div>
    </header>

    <main class="page-content">
      <div class="model-cards">
        <div class="model-card">
          <div class="card-header">
            <div class="model-icon">🤖</div>
            <div class="model-info">
              <h3 class="model-name">LLM 模型</h3>
              <p class="model-desc">大语言模型，用于对话和推理</p>
            </div>
          </div>
          <div class="card-body">
            <div class="config-item">
              <span class="config-label">模型</span>
              <span class="config-value">qwen2.5:3b</span>
            </div>
            <div class="config-item">
              <span class="config-label">提供商</span>
              <span class="config-value">Ollama</span>
            </div>
            <div class="config-item">
              <span class="config-label">地址</span>
              <span class="config-value">http://localhost:11434/v1</span>
            </div>
          </div>
          <div class="card-footer">
            <span class="status-badge ready">已连接</span>
          </div>
        </div>

        <div class="model-card">
          <div class="card-header">
            <div class="model-icon">📊</div>
            <div class="model-info">
              <h3 class="model-name">Embedding 模型</h3>
              <p class="model-desc">文本向量化模型，用于知识库检索</p>
            </div>
          </div>
          <div class="card-body">
            <div class="config-item">
              <span class="config-label">模型</span>
              <span class="config-value">nomic-embed-text:latest</span>
            </div>
            <div class="config-item">
              <span class="config-label">提供商</span>
              <span class="config-value">Ollama</span>
            </div>
            <div class="config-item">
              <span class="config-label">地址</span>
              <span class="config-value">http://localhost:11434/v1</span>
            </div>
          </div>
          <div class="card-footer">
            <span class="status-badge ready">已连接</span>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.models-page {
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

.model-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 20px;
}

.model-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.model-icon {
  width: 40px;
  height: 40px;
  background: #f3f4f6;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.model-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.model-desc {
  font-size: 13px;
  color: #6b7280;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.config-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f9fafb;
  border-radius: 8px;
}

.config-label {
  font-size: 13px;
  color: #6b7280;
}

.config-value {
  font-size: 13px;
  color: #1f2937;
  font-weight: 500;
  font-family: monospace;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
}

.status-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.ready {
  background: #dcfce7;
  color: #166534;
}
</style>
