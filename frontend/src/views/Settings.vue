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
        <!-- ========== 数据库配置 ========== -->
        <section class="setting-section">
          <h2 class="section-title">数据库配置</h2>
          <div class="setting-list">
            <!-- MySQL -->
            <div
              class="setting-item"
              @mouseenter="hoveredItem = 'mysql'"
              @mouseleave="hoveredItem = null"
            >
              <div class="setting-info">
                <span class="setting-name">MySQL 数据库</span>
                <span class="setting-desc">工作流数据持久化存储</span>
              </div>
              <div class="setting-value">
                <span class="status-badge ready">已连接</span>
                <span class="config-detail">{{ mysqlConfig.host }}:{{ mysqlConfig.port }}/{{ mysqlConfig.database }}</span>
                <div class="action-btns" v-show="hoveredItem === 'mysql'">
                  <button class="btn-icon-sm" title="编辑" @click="editConfig('mysql')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  </button>
                  <button class="btn-icon-sm" title="复制" @click="copyConfig('mysql')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                  </button>
                </div>
              </div>
            </div>

            <!-- Redis -->
            <div
              class="setting-item"
              @mouseenter="hoveredItem = 'redis'"
              @mouseleave="hoveredItem = null"
            >
              <div class="setting-info">
                <span class="setting-name">Redis 缓存</span>
                <span class="setting-desc">检查点和状态管理</span>
              </div>
              <div class="setting-value">
                <span class="status-badge ready">已连接</span>
                <span class="config-detail">{{ redisConfig.host }}:{{ redisConfig.port }}</span>
                <div class="action-btns" v-show="hoveredItem === 'redis'">
                  <button class="btn-icon-sm" title="编辑" @click="editConfig('redis')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  </button>
                  <button class="btn-icon-sm" title="复制" @click="copyConfig('redis')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                  </button>
                </div>
              </div>
            </div>

            <!-- Milvus 向量库 -->
            <div
              class="setting-item"
              :class="{ disabled: !milvusConfig.enabled }"
              @mouseenter="hoveredItem = 'milvus'"
              @mouseleave="hoveredItem = null"
            >
              <div class="setting-info">
                <span class="setting-name">Milvus 向量库</span>
                <span class="setting-desc">{{ milvusConfig.enabled ? milvusConfig.host + ':' + milvusConfig.port + ' · BM25:' + (milvusConfig.enableBM25 ? '开' : '关') + ' Reranker:' + (milvusConfig.enableReranker ? '开' : '关') : '知识库向量检索（可选）' }}</span>
              </div>
              <div class="setting-value">
                <span :class="['status-badge', milvusConfig.enabled ? 'ready' : 'disabled']">{{ milvusConfig.enabled ? '已连接' : '未启用' }}</span>
                <div class="action-btns" v-show="hoveredItem === 'milvus'">
                  <button class="btn-text" @click="toggleMilvus(true)" v-if="!milvusConfig.enabled">启用</button>
                  <button class="btn-icon-sm" title="编辑" @click="editConfig('milvus')" v-if="milvusConfig.enabled">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  </button>
                  <button class="btn-icon-sm" title="复制" @click="copyConfig('milvus')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                  </button>
                  <button class="btn-icon-sm btn-danger" title="禁用" @click="toggleMilvus(false)" v-if="milvusConfig.enabled">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- ========== 模型配置 ========== -->
        <section class="setting-section">
          <h2 class="section-title">模型配置</h2>
          <div class="setting-list">
            <!-- LLM 模型 -->
            <div
              class="setting-item"
              @mouseenter="hoveredItem = 'llm'"
              @mouseleave="hoveredItem = null"
            >
              <div class="setting-info">
                <span class="setting-name">LLM 模型</span>
                <span class="setting-desc">大语言模型用于对话推理</span>
              </div>
              <div class="setting-value">
                <span class="config-detail">{{ llmModel.name }} @ {{ llmModel.provider }}</span>
                <div class="action-btns" v-show="hoveredItem === 'llm'">
                  <button class="btn-icon-sm" title="编辑" @click="editConfig('llm')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  </button>
                  <button class="btn-icon-sm" title="复制" @click="copyConfig('llm')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                  </button>
                </div>
              </div>
            </div>

            <!-- Embedding 模型 -->
            <div
              class="setting-item"
              @mouseenter="hoveredItem = 'embedding'"
              @mouseleave="hoveredItem = null"
            >
              <div class="setting-info">
                <span class="setting-name">Embedding 模型</span>
                <span class="setting-desc">文本向量化用于知识检索</span>
              </div>
              <div class="setting-value">
                <span class="config-detail">{{ embeddingModel.name }} @ {{ embeddingModel.provider }}</span>
                <div class="action-btns" v-show="hoveredItem === 'embedding'">
                  <button class="btn-icon-sm" title="编辑" @click="editConfig('embedding')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  </button>
                  <button class="btn-icon-sm" title="复制" @click="copyConfig('embedding')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                  </button>
                </div>
              </div>
            </div>

            <!-- Reranker 模型 -->
            <div
              class="setting-item"
              :class="{ disabled: !rerankerModel.enabled }"
              @mouseenter="hoveredItem = 'reranker'"
              @mouseleave="hoveredItem = null"
            >
              <div class="setting-info">
                <span class="setting-name">Reranker 模型</span>
                <span class="setting-desc">{{ rerankerModel.enabled ? rerankerModel.name + ' @ ' + rerankerModel.provider : '检索结果重排序（可选）' }}</span>
              </div>
              <div class="setting-value">
                <span :class="['status-badge', rerankerModel.enabled ? 'ready' : 'disabled']">{{ rerankerModel.enabled ? '已启用' : '未启用' }}</span>
                <div class="action-btns" v-show="hoveredItem === 'reranker'">
                  <button class="btn-text" @click="toggleReranker(true)" v-if="!rerankerModel.enabled">启用</button>
                  <button class="btn-icon-sm" title="编辑" @click="editConfig('reranker')" v-if="rerankerModel.enabled">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 013 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                  </button>
                  <button class="btn-icon-sm" title="复制" @click="copyConfig('reranker')">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
                  </button>
                  <button class="btn-icon-sm btn-danger" title="禁用" @click="toggleReranker(false)" v-if="rerankerModel.enabled">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- ========== 系统信息 ========== -->
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

    <!-- 编辑弹窗 -->
    <Teleport to="body">
      <div class="modal-overlay" v-if="showEditModal" @click.self="showEditModal = false">
        <div class="modal-content">
          <div class="modal-header">
            <h3 class="modal-title">{{ editTitle }}</h3>
            <button class="btn-close" @click="showEditModal = false">&times;</button>
          </div>
          <div class="modal-body">
            <!-- MySQL 编辑 -->
            <template v-if="editingType === 'mysql'">
              <div class="form-group">
                <label>主机地址</label>
                <input v-model="editForm.mysql.host" type="text" class="form-input" />
              </div>
              <div class="form-row">
                <div class="form-group flex-1">
                  <label>端口</label>
                  <input v-model.number="editForm.mysql.port" type="number" class="form-input" />
                </div>
                <div class="form-group flex-1">
                  <label>数据库名</label>
                  <input v-model="editForm.mysql.database" type="text" class="form-input" />
                </div>
              </div>
              <div class="form-row">
                <div class="form-group flex-1">
                  <label>用户名</label>
                  <input v-model="editForm.mysql.username" type="text" class="form-input" />
                </div>
                <div class="form-group flex-1">
                  <label>密码</label>
                  <input v-model="editForm.mysql.password" type="password" class="form-input" />
                </div>
              </div>
            </template>

            <!-- Redis 编辑 -->
            <template v-if="editingType === 'redis'">
              <div class="form-row">
                <div class="form-group flex-1">
                  <label>主机地址</label>
                  <input v-model="editForm.redis.host" type="text" class="form-input" />
                </div>
                <div class="form-group flex-1">
                  <label>端口</label>
                  <input v-model.number="editForm.redis.port" type="number" class="form-input" />
                </div>
              </div>
              <div class="form-group">
                <label>密码（可选）</label>
                <input v-model="editForm.redis.password" type="password" class="form-input" placeholder="留空则无密码" />
              </div>
            </template>

            <!-- Milvus 编辑 -->
            <template v-if="editingType === 'milvus'">
              <div class="form-row">
                <div class="form-group flex-1">
                  <label>主机地址</label>
                  <input v-model="editForm.milvus.host" type="text" class="form-input" />
                </div>
                <div class="form-group flex-1">
                  <label>端口</label>
                  <input v-model.number="editForm.milvus.port" type="number" class="form-input" />
                </div>
              </div>
              <div class="form-row">
                <div class="form-group flex-1">
                  <label>数据库名</label>
                  <input v-model="editForm.milvus.dbName" type="text" class="form-input" />
                </div>
                <div class="form-group flex-1">
                  <label>Collection 名称</label>
                  <input v-model="editForm.milvus.collectionName" type="text" class="form-input" />
                </div>
              </div>
              <div class="form-group">
                <label>Token（可选）</label>
                <input v-model="editForm.milvus.token" type="text" class="form-input" placeholder="Milvus 认证 Token" />
              </div>
              <div class="form-checks">
                <label class="check-label">
                  <input type="checkbox" v-model="editForm.milvus.enableBM25" />
                  启用 BM25 全文检索
                </label>
                <label class="check-label">
                  <input type="checkbox" v-model="editForm.milvus.enableReranker" />
                  启用 Reranker 重排序
                </label>
              </div>
            </template>

            <!-- LLM 编辑 -->
            <template v-if="editingType === 'llm'">
              <div class="form-group">
                <label>模型名称</label>
                <select v-model="editForm.llm.name" class="form-select">
                  <option value="qwen2.5:3b">qwen2.5:3b</option>
                  <option value="qwen2.5:7b">qwen2.5:7b</option>
                  <option value="llama3:8b">llama3:8b</option>
                  <option value="deepseek-v2:16b">deepseek-v2:16b</option>
                </select>
              </div>
              <div class="form-group">
                <label>提供商</label>
                <select v-model="editForm.llm.provider" class="form-select">
                  <option value="Ollama">Ollama (本地)</option>
                  <option value="OpenAI">OpenAI</option>
                  <option value="Azure">Azure OpenAI</option>
                </select>
              </div>
              <div class="form-group">
                <label>API 地址</label>
                <input v-model="editForm.llm.baseUrl" type="text" class="form-input" />
              </div>
              <div class="form-group">
                <label>API Key（可选）</label>
                <input v-model="editForm.llm.apiKey" type="password" class="form-input" placeholder="本地 Ollama 可留空" />
              </div>
            </template>

            <!-- Embedding 编辑 -->
            <template v-if="editingType === 'embedding'">
              <div class="form-group">
                <label>模型名称</label>
                <select v-model="editForm.embedding.name" class="form-select">
                  <option value="nomic-embed-text:latest">nomic-embed-text:latest</option>
                  <option value="bge-large-zh:v1.5">bge-large-zh:v1.5</option>
                  <option value="m3e-base">m3e-base</option>
                </select>
              </div>
              <div class="form-group">
                <label>提供商</label>
                <select v-model="editForm.embedding.provider" class="form-select">
                  <option value="Ollama">Ollama (本地)</option>
                  <option value="OpenAI">OpenAI</option>
                </select>
              </div>
              <div class="form-group">
                <label>API 地址</label>
                <input v-model="editForm.embedding.baseUrl" type="text" class="form-input" />
              </div>
            </template>

            <!-- Reranker 编辑 -->
            <template v-if="editingType === 'reranker'">
              <div class="form-group">
                <label>模型名称</label>
                <select v-model="editForm.reranker.name" class="form-select">
                  <option value="gte-rerank">gte-rerank</option>
                  <option value="bge-reranker-v2-m3">bge-reranker-v2-m3</option>
                  <option value="cross-encoder">cross-encoder</option>
                </select>
              </div>
              <div class="form-group">
                <label>提供商</label>
                <select v-model="editForm.reranker.provider" class="form-select">
                  <option value="Ollama">Ollama (本地)</option>
                  <option value="OpenAI">OpenAI</option>
                </select>
              </div>
              <div class="form-group">
                <label>API 地址</label>
                <input v-model="editForm.reranker.baseUrl" type="text" class="form-input" />
              </div>
            </template>
          </div>
          <div class="modal-footer">
            <button class="btn-cancel" @click="showEditModal = false">取消</button>
            <button class="btn-save" @click="saveEdit">保存</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Toast 提示 -->
    <Transition name="toast">
      <div class="toast" v-if="toast.show">{{ toast.message }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'

// ========== Hover 状态 ==========
const hoveredItem = ref<string | null>(null)

// ========== 数据库配置 ==========
const mysqlConfig = reactive({
  host: 'localhost',
  port: 3306,
  database: 'langgraph4j-engine',
  username: 'root',
  password: '',
})

const redisConfig = reactive({
  host: 'localhost',
  port: 6379,
  password: '',
})

const milvusConfig = reactive({
  enabled: true,
  host: 'localhost',
  port: 19530,
  dbName: 'default',
  collectionName: 'knowledge_base',
  token: '',
  enableBM25: true,
  enableReranker: true,
})

// ========== 模型配置 ==========
const llmModel = reactive({
  name: 'qwen2.5:3b',
  provider: 'Ollama',
  baseUrl: 'http://localhost:11434',
  apiKey: '',
})

const embeddingModel = reactive({
  name: 'nomic-embed-text:latest',
  provider: 'Ollama',
  baseUrl: 'http://localhost:11434',
})

const rerankerModel = reactive({
  enabled: true,
  name: 'gte-rerank',
  provider: 'Ollama',
  baseUrl: 'http://localhost:11434',
})

// ========== 编辑弹窗 ==========
const showEditModal = ref(false)
const editingType = ref('')
const editTitle = ref('')
const editForm = reactive({
  mysql: { ...mysqlConfig },
  redis: { ...redisConfig },
  milvus: { ...milvusConfig },
  llm: { ...llmModel },
  embedding: { ...embeddingModel },
  reranker: { ...rerankerModel },
})

function editConfig(type: string) {
  editingType.value = type
  const titles: Record<string, string> = {
    mysql: '编辑 MySQL 配置',
    redis: '编辑 Redis 配置',
    milvus: '编辑 Milvus 配置',
    llm: '编辑 LLM 模型',
    embedding: '编辑 Embedding 模型',
    reranker: '编辑 Reranker 模型',
  }
  editTitle.value = titles[type] || '编辑配置'
  // 复制当前值到表单
  if (type === 'mysql') Object.assign(editForm.mysql, mysqlConfig)
  if (type === 'redis') Object.assign(editForm.redis, redisConfig)
  if (type === 'milvus') Object.assign(editForm.milvus, milvusConfig)
  if (type === 'llm') Object.assign(editForm.llm, llmModel)
  if (type === 'embedding') Object.assign(editForm.embedding, embeddingModel)
  if (type === 'reranker') Object.assign(editForm.reranker, rerankerModel)
  showEditModal.value = true
}

function saveEdit() {
  const t = editingType.value
  if (t === 'mysql') Object.assign(mysqlConfig, editForm.mysql)
  if (t === 'redis') Object.assign(redisConfig, editForm.redis)
  if (t === 'milvus') Object.assign(milvusConfig, editForm.milvus)
  if (t === 'llm') Object.assign(llmModel, editForm.llm)
  if (t === 'embedding') Object.assign(embeddingModel, editForm.embedding)
  if (t === 'reranker') Object.assign(rerankerModel, editForm.reranker)
  showEditModal.value = false
  showToast('配置已保存')
}

function copyConfig(type: string) {
  const data: Record<string, any> = {
    mysql: mysqlConfig,
    redis: redisConfig,
    milvus: milvusConfig,
    llm: llmModel,
    embedding: embeddingModel,
    reranker: rerankerModel,
  }
  navigator.clipboard.writeText(JSON.stringify(data[type], null, 2))
  showToast('配置已复制到剪贴板')
}

function toggleMilvus(enabled: boolean) {
  milvusConfig.enabled = enabled
  showToast(enabled ? 'Milvus 已启用' : 'Milvus 已禁用')
}

function toggleReranker(enabled: boolean) {
  rerankerModel.enabled = enabled
  showToast(enabled ? 'Reranker 已启用' : 'Reranker 已禁用')
}

// ========== Toast ==========
const toast = reactive({ show: false, message: '' })
let toastTimer: ReturnType<typeof setTimeout> | null = null

function showToast(msg: string) {
  toast.show = true
  toast.message = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.show = false }, 2000)
}
</script>

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
  padding: 14px 8px;
  border-bottom: 1px solid #f3f4f6;
  transition: background 0.15s;
  border-radius: 6px;
}

.setting-item:hover {
  background: #fafbfc;
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-item.disabled {
  opacity: 0.55;
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
  gap: 10px;
}

.status-badge {
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
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
  font-family: 'SF Mono', Monaco, monospace;
}

/* 操作按钮 */
.action-btns {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 4px;
}

.btn-text {
  padding: 4px 10px;
  font-size: 12px;
  color: #3b82f6;
  background: none;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-text:hover {
  background: #eff6ff;
  border-color: #93c5fd;
}

.btn-icon-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-icon-sm:hover {
  background: #f3f4f6;
  color: #374151;
}

.btn-icon-sm.btn-danger:hover {
  background: #fef2f2;
  color: #ef4444;
}

/* ====== 弹窗 ====== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  border-radius: 16px;
  width: 520px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-bottom: 1px solid #f3f4f6;
}

.modal-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.btn-close {
  width: 28px;
  height: 28px;
  border: none;
  background: #f3f4f6;
  border-radius: 50%;
  cursor: pointer;
  font-size: 16px;
  color: #6b7280;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-close:hover {
  background: #e5e7eb;
}

.modal-body {
  padding: 20px 22px;
  max-height: 60vh;
  overflow-y: auto;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 14px;
}

.form-group label {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.form-row {
  display: flex;
  gap: 12px;
}

.form-row .form-group {
  flex: 1;
}

.flex-1 { flex: 1; }

.form-input, .form-select {
  height: 36px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  color: #1f2937;
  outline: none;
  transition: border-color 0.15s;
  background: #fff;
}

.form-input:focus, .form-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-checks {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}

.check-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
}

.check-label input[type="checkbox"] {
  accent-color: #3b82f6;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 22px;
  border-top: 1px solid #f3f4f6;
}

.btn-cancel {
  padding: 7px 18px;
  font-size: 13px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-cancel:hover {
  background: #f9fafb;
}

.btn-save {
  padding: 7px 18px;
  font-size: 13px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  cursor: pointer;
  transition: all 0.15s;
  font-weight: 500;
}

.btn-save:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

/* ====== Toast ====== */
.toast {
  position: fixed;
  bottom: 32px;
  left: 50%;
  transform: translateX(-50%);
  padding: 10px 22px;
  background: #1f2937;
  color: #fff;
  font-size: 13px;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  z-index: 2000;
}

.toast-enter-active, .toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from, .toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(10px);
}
</style>
