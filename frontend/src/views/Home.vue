<template>
  <div class="home">
    <header class="header">
      <div class="logo">
        <span class="logo-icon">◈</span>
        <span class="logo-text">LangGraph4J</span>
      </div>
      <nav class="nav">
        <router-link to="/workflows" class="nav-link">工作流</router-link>
        <router-link to="/editor" class="nav-link primary">新建工作流</router-link>
      </nav>
    </header>
    
    <main class="main">
      <section class="hero">
        <h1 class="hero-title">
          可视化 AI 工作流引擎
        </h1>
        <p class="hero-subtitle">
          基于 LangGraph4J 的强大工作流编排平台<br>
          拖拽式画布、多节点类型、支持 LLM/Agent/RAG
        </p>
        <div class="hero-actions">
          <router-link to="/editor" class="btn btn-primary">
            开始创建
          </router-link>
          <router-link to="/workflows" class="btn btn-secondary">
            查看示例
          </router-link>
        </div>
      </section>
      
      <section class="features">
        <h2 class="section-title">核心特性</h2>
        <div class="feature-grid">
          <div class="feature-card">
            <div class="feature-icon" style="background: #8b5cf6;">🤖</div>
            <h3 class="feature-title">LLM 节点</h3>
            <p class="feature-desc">支持多种大语言模型，灵活配置提示词和参数</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon" style="background: #f59e0b;">🎯</div>
            <h3 class="feature-title">Agent 节点</h3>
            <p class="feature-desc">智能体支持工具调用，实现复杂任务自动化</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon" style="background: #06b6d4;">📚</div>
            <h3 class="feature-title">知识检索</h3>
            <p class="feature-desc">RAG 支持，从知识库检索相关内容增强回答</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon" style="background: #ec4899;">◆</div>
            <h3 class="feature-title">条件分支</h3>
            <p class="feature-desc">灵活的条件判断，支持多分支流程控制</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon" style="background: #6366f1;">💻</div>
            <h3 class="feature-title">代码执行</h3>
            <p class="feature-desc">自定义代码节点，实现任意逻辑处理</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon" style="background: #84cc16;">🌐</div>
            <h3 class="feature-title">HTTP 请求</h3>
            <p class="feature-desc">调用外部 API，与第三方服务集成</p>
          </div>
        </div>
      </section>
      
      <section class="architecture">
        <h2 class="section-title">技术架构</h2>
        <div class="arch-content">
          <div class="arch-item">
            <h4>后端</h4>
            <ul>
              <li>Java 17 + Vert.x 异步框架</li>
              <li>LangGraph4J 流程编排引擎</li>
              <li>Redis 分布式状态管理</li>
              <li>PGVector 向量数据库</li>
            </ul>
          </div>
          <div class="arch-item">
            <h4>前端</h4>
            <ul>
              <li>Vue 3 + TypeScript</li>
              <li>Pinia 状态管理</li>
              <li>自由拖拽画布</li>
              <li>Tailwind CSS</li>
            </ul>
          </div>
        </div>
      </section>
    </main>
    
    <footer class="footer">
      <p>© 2024 LangGraph4J - Visual AI Workflow Engine</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
// Home page
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 32px;
  background: white;
  border-bottom: 1px solid #e2e8f0;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  font-size: 28px;
  color: #3b82f6;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.nav {
  display: flex;
  gap: 16px;
}

.nav-link {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  text-decoration: none;
  transition: all 0.2s;
}

.nav-link:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.nav-link.primary {
  background: #3b82f6;
  color: white;
}

.nav-link.primary:hover {
  background: #2563eb;
}

.main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 64px 32px;
}

.hero {
  text-align: center;
  padding: 64px 0;
}

.hero-title {
  font-size: 48px;
  font-weight: 800;
  color: #1e293b;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-size: 18px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 32px;
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.btn {
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background: #2563eb;
}

.btn-secondary {
  background: white;
  color: #64748b;
  border: 1px solid #e2e8f0;
}

.btn-secondary:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.features {
  padding: 64px 0;
}

.section-title {
  font-size: 32px;
  font-weight: 700;
  color: #1e293b;
  text-align: center;
  margin-bottom: 48px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.feature-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.2s;
}

.feature-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 16px;
}

.feature-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
}

.feature-desc {
  font-size: 14px;
  color: #64748b;
  line-height: 1.5;
}

.architecture {
  padding: 64px 0;
}

.arch-content {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 32px;
  max-width: 800px;
  margin: 0 auto;
}

.arch-item {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.arch-item h4 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16px;
}

.arch-item ul {
  list-style: none;
}

.arch-item li {
  font-size: 14px;
  color: #64748b;
  padding: 8px 0;
  padding-left: 20px;
  position: relative;
}

.arch-item li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #3b82f6;
  font-weight: bold;
}

.footer {
  text-align: center;
  padding: 32px;
  border-top: 1px solid #e2e8f0;
  color: #94a3b8;
  font-size: 14px;
}

@media (max-width: 768px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }
  
  .arch-content {
    grid-template-columns: 1fr;
  }
  
  .hero-title {
    font-size: 32px;
  }
}
</style>
