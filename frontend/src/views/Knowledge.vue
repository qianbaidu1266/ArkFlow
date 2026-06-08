<template>
  <div class="knowledge-page">
    <!-- 页面头部 -->
    <header class="page-header">
      <div class="header-left">
        <h1 class="page-title">知识库</h1>
        <p class="page-desc">管理 RAG 知识库，支持文档上传（TXT / Markdown / PDF / Word / Excel）、分块编辑和混合检索</p>
      </div>
      <button class="btn btn-primary" @click="showCreateDialog = true">
        <Plus :size="16" />
        新建知识库
      </button>
    </header>

    <!-- 知识库列表 -->
    <main class="page-content" v-if="!selectedKb">
      <div v-if="loading" class="loading-state">加载中...</div>
      <div v-else-if="kbs.length === 0" class="empty-state">
        <div class="empty-icon">📚</div>
        <div class="empty-title">暂无知识库</div>
        <div class="empty-desc">创建知识库后，上传文档即可开始使用向量检索</div>
      </div>
      <div v-else class="kb-grid">
        <div v-for="kb in kbs" :key="kb.id" class="kb-card" @click="selectKb(kb)">
          <div class="kb-card-header">
            <div class="kb-icon">
              <BookOpen :size="20" />
            </div>
            <div class="kb-actions">
              <button class="icon-btn" @click.stop="editKb(kb)" title="编辑">
                <Pencil :size="14" />
              </button>
              <button class="icon-btn danger" @click.stop="confirmDeleteKb(kb)" title="删除">
                <Trash2 :size="14" />
              </button>
            </div>
          </div>
          <div class="kb-card-body">
            <h3 class="kb-name">{{ kb.name }}</h3>
            <p class="kb-desc" v-if="kb.description">{{ kb.description }}</p>
          </div>
          <div class="kb-card-footer">
            <span class="kb-stat">
              <FileText :size="14" />
              {{ kb.docCount || 0 }} 文档
            </span>
            <span class="kb-stat">
              <Grid3x3 :size="14" />
              {{ kb.chunkCount || 0 }} 分块
            </span>
            <span class="kb-badge">{{ searchTypeLabel(kb.searchType) }}</span>
          </div>
        </div>
      </div>
    </main>

    <!-- 知识库详情 -->
    <div v-else class="kb-detail">
      <!-- 返回 -->
      <div class="detail-header">
        <button class="back-btn" @click="selectedKb = null">
          <ArrowLeft :size="16" />
          返回列表
        </button>
        <div class="detail-tabs">
          <button
            v-for="tab in detailTabs"
            :key="tab.key"
            class="tab-btn"
            :class="{ active: activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>
      </div>

      <!-- 文档列表 -->
      <div v-if="activeTab === 'documents'" class="documents-section">
        <div class="section-header">
          <h2>{{ selectedKb.name }} - 文档</h2>
          <div class="section-actions">
            <label class="btn btn-primary upload-btn">
              <Upload :size="14" />
              上传文档
              <input
                type="file"
                accept=".txt,.md,.pdf,.docx,.doc,.xlsx"
                style="display:none"
                @change="onFileUpload"
              />
            </label>
          </div>
        </div>

        <div v-if="documents.length === 0" class="empty-state small">
          <div class="empty-icon">📄</div>
          <div class="empty-title">暂无文档</div>
          <div class="empty-desc">上传 TXT、Markdown、PDF、Word 或 Excel 文件</div>
        </div>

        <div v-else class="doc-list">
          <div v-for="doc in documents" :key="doc.id" class="doc-item">
            <div class="doc-info">
              <span class="doc-icon">{{ fileIcon(doc.fileType) }}</span>
              <div class="doc-meta">
                <span class="doc-name">{{ doc.sourceFileName }}</span>
                <span class="doc-size">{{ formatSize(doc.fileSize) }}</span>
              </div>
            </div>
            <div class="doc-status">
              <span class="status-tag" :class="doc.indexingStatus">
                {{ statusLabel(doc.indexingStatus) }}
              </span>
            </div>
            <div class="doc-actions">
              <button
                class="icon-btn"
                @click="viewChunks(doc)"
                title="查看分块"
                v-if="doc.indexingStatus === 'completed'"
              >
                <Grid3x3 :size="14" />
              </button>
              <button class="icon-btn danger" @click="confirmDeleteDoc(doc)" title="删除">
                <Trash2 :size="14" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 知识库设置（Dify 风格） -->
      <div v-if="activeTab === 'settings'" class="settings-section">
        <div class="section-header">
          <h2>知识库设置</h2>
          <span class="section-hint">在此配置此知识库的属性和检索参数</span>
        </div>

        <div class="dify-settings-form">
          <!-- 名称和描述 -->
          <div class="setting-group">
            <label class="setting-label">名称和描述</label>
            <div class="setting-field">
              <div class="field-row">
                <div class="field-icon">📚</div>
                <input
                  v-model="kbSettings.name"
                  type="text"
                  class="form-input flex-1"
                  placeholder="知识库名称"
                />
              </div>
              <textarea
                v-model="kbSettings.description"
                class="form-textarea mt-2"
                rows="3"
                placeholder="描述该数据集的内容。详细描述可以让 AI 更快地定位到数据集的内容，如果为空，Dify 将使用默认的命中策略。"
              ></textarea>
            </div>
          </div>

          <!-- 分段模式（Dify 风格） -->
          <div class="setting-group">
            <label class="setting-label">
              分段设置
              <a href="#" class="help-link" title="了解分段模式">了解更多关于分段模式</a>
            </label>

            <!-- 模式选择卡片 -->
            <div class="mode-cards" :class="{ 'has-expand': kbSettings.segmentationMode !== '' }">
              <!-- General -->
              <div
                class="mode-card"
                :class="{ active: kbSettings.segmentationMode === 'general' }"
                @click="kbSettings.segmentationMode = 'general'"
              >
                <div class="mode-icon">📝</div>
                <div class="mode-name">通用</div>
                <div class="mode-desc">适用文本分块模式，检索和召回的块是相同的</div>
              </div>
              <!-- Parent-Child -->
              <div
                class="mode-card"
                :class="{ active: kbSettings.segmentationMode === 'parent-child' }"
                @click="kbSettings.segmentationMode = 'parent-child'"
              >
                <div class="mode-icon">👨‍👩‍👧</div>
                <div class="mode-name">父子分段</div>
                <div class="mode-desc">使用父子模式时，子块用于检索，父块用作上下文</div>
              </div>
              <!-- Q&A -->
              <div
                class="mode-card"
                :class="{ active: kbSettings.segmentationMode === 'qa' }"
                @click="kbSettings.segmentationMode = 'qa'"
              >
                <div class="mode-icon">❓</div>
                <div class="mode-name">Q&A</div>
                <div class="mode-desc">使用 Q&A 模式时，将按问答分割为问题对和答案</div>
              </div>
            </div>

            <!-- ========== 通用模式配置 ========== -->
            <div v-if="kbSettings.segmentationMode === 'general'" class="seg-config-panel">
              <div class="seg-header">
                <span class="seg-header-icon">⚙️</span>
                <span class="seg-header-title">通用</span>
                <span class="seg-header-desc">适用文本分块模式，检索和召回的块是相同的</span>
              </div>

              <!-- 基础参数 -->
              <div class="seg-row-3">
                <div class="seg-field">
                  <label class="seg-label">分段标识符 ⓘ</label>
                  <input v-model="chunkConfig.separator" type="text" class="form-input form-input-sm" placeholder="\n\n" />
                </div>
                <div class="seg-field">
                  <label class="seg-label">分段最大长度 ⓘ</label>
                  <div class="input-with-unit">
                    <input v-model.number="chunkConfig.chunkSize" type="number" min="100" max="8000" class="form-input form-input-sm" />
                    <span class="unit">characters</span>
                  </div>
                </div>
                <div class="seg-field">
                  <label class="seg-label">分段重叠长度 ⓘ</label>
                  <div class="input-with-unit">
                    <input v-model.number="chunkConfig.chunkOverlap" type="number" min="0" max="500" class="form-input form-input-sm" />
                    <span class="unit">characters</span>
                  </div>
                </div>
              </div>

              <!-- 文本预处理规则 -->
              <div class="preprocess-rules">
                <label class="seg-subtitle">文本预处理规则</label>
                <label class="check-item">
                  <input type="checkbox" v-model="chunkConfig.removeExtraSpaces" checked />
                  替换连续的空格、换行符和制表符
                </label>
                <label class="check-item">
                  <input type="checkbox" v-model="chunkConfig.removeUrls" />
                  删除所有 URL 和电子邮件地址
                </label>
              </div>

              <!-- 操作按钮 -->
              <div class="seg-actions">
                <button class="btn-preview" @click="previewChunks">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  预览块
                </button>
                <button class="btn-reset" @click="resetChunkConfig">重置</button>
              </div>
            </div>

            <!-- ========== 父子分段配置 ========== -->
            <div v-if="kbSettings.segmentationMode === 'parent-child'" class="seg-config-panel">
              <div class="seg-header">
                <span class="seg-header-icon">👨‍👩‍👧</span>
                <span class="seg-header-title">父子分段</span>
                <span class="seg-header-desc">使用父子模式时，子块用于检索，父块用作上下文</span>
              </div>

              <!-- 父块用作上下文 -->
              <div class="seg-subsection">
                <label class="seg-subtitle">父块用作上下文</label>
                <div class="parent-mode-cards">
                  <div
                    class="pmode-card"
                    :class="{ active: parentChunkMode === 'paragraph' }"
                    @click="parentChunkMode = 'paragraph'"
                  >
                    <div class="pmode-radio" :class="{ checked: parentChunkMode === 'paragraph' }"></div>
                    <div class="pmode-content">
                      <div class="pmode-name">段落</div>
                      <div class="pmode-desc">此模式根据段落和最大长度将文本拆分为段落，使用拆分文本作为检索的父块</div>
                      <div class="pmode-fields" v-if="parentChunkMode === 'paragraph'">
                        <div class="seg-field-inline">
                          <label class="seg-label-xs">分段标识符 ⓘ</label>
                          <input value="\n\n" type="text" class="form-input form-input-xs" readonly />
                        </div>
                        <div class="seg-field-inline">
                          <label class="seg-label-xs">分段最大长度 ⓘ</label>
                          <div class="input-with-unit">
                            <input v-model.number="parentChunkSize" type="number" class="form-input form-input-xs" />
                            <span class="unit-xs">characters</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div
                    class="pmode-card"
                    :class="{ active: parentChunkMode === 'fulltext' }"
                    @click="parentChunkMode = 'fulltext'"
                  >
                    <div class="pmode-radio" :class="{ checked: parentChunkMode === 'fulltext' }"></div>
                    <div class="pmode-content">
                      <div class="pmode-name">全文</div>
                      <div class="pmode-desc">整个文档用作父块并直接检索。注意，出于性能原因，超过 10000 个标记的文本将被自动截断。</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 子块用于检索 -->
              <div class="seg-subsection mt-3">
                <label class="seg-subtitle">子块用于检索</label>
                <div class="seg-row-2">
                  <div class="seg-field">
                    <label class="seg-label">分段标识符 ⓘ</label>
                    <input value="\n" type="text" class="form-input form-input-sm" readonly />
                  </div>
                  <div class="seg-field">
                    <label class="seg-label">分段最大长度 ⓘ</label>
                    <div class="input-with-unit">
                      <input v-model.number="childChunkSize" type="number" min="100" max="4000" class="form-input form-input-sm" />
                      <span class="unit">characters</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 文本预处理规则 -->
              <div class="preprocess-rules mt-2">
                <label class="seg-subtitle">文本预处理规则</label>
                <label class="check-item">
                  <input type="checkbox" v-model="chunkConfig.removeExtraSpaces" checked />
                  替换连续的空格、换行符和制表符
                </label>
                <label class="check-item">
                  <input type="checkbox" v-model="chunkConfig.removeUrls" />
                  删除所有 URL 和电子邮件地址
                </label>
              </div>

              <div class="seg-actions">
                <button class="btn-preview" @click="previewChunks">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  预览块
                </button>
                <button class="btn-reset" @click="resetChunkConfig">重置</button>
              </div>
            </div>

            <!-- ========== Q&A 模式配置 ========== -->
            <div v-if="kbSettings.segmentationMode === 'qa'" class="seg-config-panel">
              <div class="seg-header">
                <span class="seg-header-icon">❓</span>
                <span class="seg-header-title">Q&A</span>
                <span class="seg-header-desc">使用 Q&A 模式时，将按问答分割为问题对和答案</span>
              </div>

              <div class="seg-row-2">
                <div class="seg-field">
                  <label class="seg-label">问题标识符 ⓘ</label>
                  <input value="Q:" type="text" class="form-input form-input-sm" placeholder="Q:" />
                </div>
                <div class="seg-field">
                  <label class="seg-label">答案标识符 ⓘ</label>
                  <input value="A:" type="text" class="form-input form-input-sm" placeholder="A:" />
                </div>
              </div>

              <div class="seg-row-2 mt-2">
                <div class="seg-field">
                  <label class="seg-label">问题最大长度 ⓘ</label>
                  <div class="input-with-unit">
                    <input type="number" value="512" min="100" max="2000" class="form-input form-input-sm" />
                    <span class="unit">characters</span>
                  </div>
                </div>
                <div class="seg-field">
                  <label class="seg-label">答案最大长度 ⓘ</label>
                  <div class="input-with-unit">
                    <input type="number" value="1024" min="100" max="4000" class="form-input form-input-sm" />
                    <span class="unit">characters</span>
                  </div>
                </div>
              </div>

              <div class="seg-actions">
                <button class="btn-preview" @click="previewChunks">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  预览块
                </button>
                <button class="btn-reset" @click="resetChunkConfig">重置</button>
              </div>
            </div>
          </div>

          <!-- Embedding 模型 -->
          <div class="setting-group">
            <label class="setting-label">Embedding 模型</label>
            <div class="setting-field">
              <select v-model="kbSettings.embeddingModel" class="form-select">
                <option value="">请选择模型</option>
                <option value="nomic-embed-text:latest">text-embedding-v3</option>
                <option value="bge-large-zh:v1.5">bge-large-zh</option>
                <option value="m3e-base">m3e-base</option>
              </select>
            </div>
          </div>

          <!-- 检索设置 -->
          <div class="setting-group">
            <label class="setting-label">
              检索设置
              <a href="#" class="help-link" title="了解检索方法">了解更多关于检索方法</a>
            </label>
            <div class="retrieval-methods">
              <!-- 向量检索 -->
              <div
                class="method-card"
                :class="{ active: kbSettings.retrievalMethod === 'vector' }"
                @click="kbSettings.retrievalMethod = 'vector'"
              >
                <div class="method-icon">📐</div>
                <div class="method-name">向量检索</div>
                <div class="method-desc">通过生成查询嵌入并查找与其最相似的文本片段</div>

                <!-- 向量检索配置（选中时展开） -->
                <div v-if="kbSettings.retrievalMethod === 'vector'" class="method-expand-config">
                  <div class="config-fields">
                    <div class="config-field">
                      <label class="field-label-xs">Top K ⓘ</label>
                      <input type="number" v-model.number="kbSettings.topK" min="1" max="50" class="form-input form-input-sm" />
                    </div>
                    <div class="config-field">
                      <label class="field-label-xs">Score 阈值 ⓘ</label>
                      <input type="number" v-model.number="kbSettings.scoreThreshold" min="0" max="1" step="0.05" class="form-input form-input-sm" />
                    </div>
                  </div>
                  <div class="config-field mt-2">
                    <label class="field-label-xs">相似度计算 ⓘ</label>
                    <select class="form-select form-select-sm">
                      <option value="cosine">余弦相似度 (Cosine)</option>
                      <option value="euclidean">欧氏距离 (Euclidean)</option>
                      <option value="dotproduct">内积 (Dot Product)</option>
                    </select>
                  </div>
                </div>
              </div>

              <!-- 全文检索 -->
              <div
                class="method-card"
                :class="{ active: kbSettings.retrievalMethod === 'fulltext' }"
                @click="kbSettings.retrievalMethod = 'fulltext'"
              >
                <div class="method-icon">📄</div>
                <div class="method-name">全文检索</div>
                <div class="method-desc">索引文件中的所有词汇，从而允许用户查询单词汇，并返回包含这些词汇的文本片段</div>

                <!-- 全文检索配置（选中时展开） -->
                <div v-if="kbSettings.retrievalMethod === 'fulltext'" class="method-expand-config">
                  <div class="config-fields">
                    <div class="config-field">
                      <label class="field-label-xs">返回数量 ⓘ</label>
                      <input type="number" v-model.number="kbSettings.topK" min="1" max="50" class="form-input form-input-sm" />
                    </div>
                    <div class="config-field">
                      <label class="field-label-xs">最小匹配度 ⓘ</label>
                      <input type="number" v-model.number="kbSettings.scoreThreshold" min="0" max="1" step="0.05" class="form-input form-input-sm" />
                    </div>
                  </div>
                  <div class="config-field mt-2">
                    <label class="field-label-xs">关键词数量 ⓘ</label>
                    <div class="slider-row">
                      <input type="range" min="1" max="20" v-model.number="bm25KeywordCount" class="range-slider range-sm" />
                      <span class="range-value">{{ bm25KeywordCount }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 混合检索（推荐） -->
              <div
                class="method-card method-card-hybrid recommended"
                :class="{ active: kbSettings.retrievalMethod === 'hybrid' }"
                @click="kbSettings.retrievalMethod = 'hybrid'"
              >
                <div class="method-badge">推荐</div>
                <div class="method-icon">🔄</div>
                <div class="method-name">混合检索</div>
                <div class="method-desc">同时执行全文检索和向量检索，并应用重排序步骤，从两套查询结果中选择匹配用户问题的最佳结果。</div>

                <!-- 混合检索配置（选中时展开） -->
                <div v-if="kbSettings.retrievalMethod === 'hybrid'" class="method-expand-config hybrid-options">
                  <!-- 权重设置 / Rerank 模型 选择 -->
                  <div class="hybrid-sub-cards">
                    <!-- 权重设置 -->
                    <div
                      class="sub-option-card"
                      :class="{ active: !kbSettings.enableReranker }"
                      @click.stop="kbSettings.enableReranker = false"
                    >
                      <div class="sub-option-icon">⚖️</div>
                      <div class="sub-option-content">
                        <div class="sub-option-header">
                          <span class="sub-option-name">权重设置</span>
                          <span class="radio-dot" :class="{ checked: !kbSettings.enableReranker }"></span>
                        </div>
                        <p class="sub-option-desc">通过调整分配的权重，重新排序两路召回结果</p>
                      </div>
                    </div>

                    <!-- Rerank 模型 -->
                    <div
                      class="sub-option-card"
                      :class="{ active: kbSettings.enableReranker }"
                      @click.stop="kbSettings.enableReranker = true"
                    >
                      <div class="sub-option-icon">🔀</div>
                      <div class="sub-option-content">
                        <div class="sub-option-header">
                          <span class="sub-option-name">Rerank 模型</span>
                          <span class="radio-dot" :class="{ checked: kbSettings.enableReranker }"></span>
                        </div>
                        <p class="sub-option-desc">使用 Rerank 模型对结果重新排序，提升相关性</p>
                      </div>
                    </div>
                  </div>

                  <!-- Rerank 模型配置（选中时显示） -->
                  <div v-if="kbSettings.enableReranker" class="rerank-config">
                    <select v-model="kbSettings.rerankerModel" class="form-select rerank-select">
                      <option value="">请选择模型</option>
                      <option value="gte-rerank">gte-rerank</option>
                      <option value="bge-reranker-v2-m3">bge-reranker-v2-m3</option>
                      <option value="cross-encoder">cross-encoder</option>
                    </select>

                    <div class="rerank-fields">
                      <div class="rerank-field">
                        <label class="field-label-xs">Top K ⓘ</label>
                        <input type="number" v-model.number="kbSettings.topK" min="1" max="20" class="form-input form-input-sm" />
                      </div>
                      <div class="rerank-field">
                        <label class="field-label-xs">Score 阈值 ⓘ</label>
                        <input type="number" v-model.number="kbSettings.scoreThreshold" min="0" max="1" step="0.05" class="form-input form-input-sm" />
                      </div>
                    </div>
                  </div>

                  <!-- 权重配置（未选 Rerank 时显示） -->
                  <div v-else class="weight-config">
                    <div class="weight-row">
                      <label class="field-label-xs">向量权重</label>
                      <div class="weight-slider-wrap">
                        <input type="range" min="0" max="100" v-model.number="vectorWeight" class="range-slider range-sm" />
                        <span class="weight-value">{{ vectorWeight }}%</span>
                      </div>
                    </div>
                    <div class="weight-row">
                      <label class="field-label-xs">全文权重</label>
                      <div class="weight-slider-wrap">
                        <input type="range" min="0" max="100" :value="fulltextWeight" class="range-slider range-sm" disabled />
                        <span class="weight-value">{{ fulltextWeight }}%</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 保存按钮 -->
        <div class="settings-footer">
          <button class="btn-save-primary" @click="saveKbSettings">
            保存
          </button>
        </div>
      </div>
      <div v-if="activeTab === 'search'" class="search-section">
        <!-- 搜索区域 -->
        <div class="search-hero">
          <div class="search-hero-icon">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.34-4.34"/></svg>
          </div>
          <h3 class="search-hero-title">检索测试</h3>
          <p class="search-hero-desc">在「{{ selectedKb.name }}」中测试向量检索、全文检索和混合检索效果</p>

          <!-- 搜索输入框 -->
          <div class="search-input-wrap" :class="{ focused: searchFocused }">
            <div class="search-input-inner">
              <svg class="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.34-4.34"/></svg>
              <input
                v-model="searchQuery"
                class="search-input"
                placeholder="输入查询内容，按 Enter 或点击搜索..."
                @focus="searchFocused = true"
                @blur="searchFocused = false"
                @keyup.enter="doSearch"
              />
              <button class="btn-search" @click="doSearch" :disabled="searching || !searchQuery.trim()">
                <svg v-if="!searching" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.34-4.34"/></svg>
                <span v-else class="spinner-sm"></span>
                {{ searching ? '搜索中' : '搜索' }}
              </button>
            </div>

            <!-- 检索模式选择 -->
            <div class="search-mode-bar">
              <button
                v-for="mode in searchModes"
                :key="mode.value"
                class="mode-chip"
                :class="{ active: searchType === mode.value }"
                @click="searchType = mode.value"
              >
                <span class="mode-icon">{{ mode.icon }}</span>
                <span>{{ mode.label }}</span>
              </button>
              <div class="mode-divider"></div>
              <select v-model.number="searchTopK" class="topk-select">
                <option :value="3">Top 3</option>
                <option :value="5">Top 5</option>
                <option :value="10">Top 10</option>
                <option :value="20">Top 20</option>
              </select>
            </div>
          </div>
        </div>

        <!-- 结果区域 -->
        <template v-if="searchResults.length > 0">
          <div class="results-header">
            <span class="results-count">找到 <strong>{{ searchResults.length }}</strong> 条相关结果</span>
            <span class="results-meta">{{ searchTypeLabel(searchType) }} · Top {{ searchTopK }}</span>
          </div>
          <div class="results-list">
            <TransitionGroup name="result">
              <div v-for="(result, idx) in searchResults" :key="idx" class="result-card">
                <div class="result-card-left">
                  <div class="result-rank-badge" :class="'rank-' + (idx + 1)">
                    {{ idx + 1 }}
                  </div>
                </div>
                <div class="result-card-body">
                  <div class="result-card-top">
                    <span class="result-source" v-if="result.documentName || result.chunkIndex !== undefined">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                      {{ result.documentName || `分块 #${(result.chunkIndex ?? idx) + 1}` }}
                    </span>
                    <div class="result-score-bar">
                      <div class="score-track">
                        <div class="score-fill" :style="{ width: Math.min(result.score * 100, 100) + '%' }"></div>
                      </div>
                      <span class="score-text">{{ (result.score * 100).toFixed(1) }}%</span>
                    </div>
                  </div>
                  <p class="result-text">{{ result.content }}</p>
                  <div class="result-tags" v-if="result.method">
                    <span class="tag tag-method" :class="'tag-' + result.method">{{ methodLabel(result.method) }}</span>
                    <span class="tag" v-if="result.bm25Score">BM25: {{ (result.bm25Score * 100).toFixed(1) }}%</span>
                    <span class="tag" v-if="result.vectorScore">Vec: {{ (result.vectorScore * 100).toFixed(1) }}%</span>
                  </div>
                </div>
              </div>
            </TransitionGroup>
          </div>
        </template>

        <!-- 空状态 -->
        <div v-else-if="searched" class="empty-state-search">
          <div class="empty-icon-lg">🔍</div>
          <h4>未找到相关结果</h4>
          <p>尝试更换关键词或调整检索模式</p>
          <button class="btn btn-outline" @click="searchQuery = ''; searched = false">清除查询</button>
        </div>

        <!-- 初始提示 -->
        <div v-else class="empty-hint">
          <div class="hint-cards">
            <div class="hint-card">
              <div class="hint-card-icon">📐</div>
              <div class="hint-card-title">向量检索</div>
              <div class="hint-card-desc">基于语义相似度匹配，适合概念性查询</div>
            </div>
            <div class="hint-card">
              <div class="hint-card-icon">📄</div>
              <div class="hint-card-title">全文检索</div>
              <div class="hint-card-desc">基于关键词精确匹配，适合专有名词查询</div>
            </div>
            <div class="hint-card hint-card-active">
              <div class="hint-card-icon">🔄</div>
              <div class="hint-card-title">混合检索</div>
              <div class="hint-card-desc">融合两种方式 + Rerank 重排序，推荐使用</div>
              <span class="badge-rec">推荐</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 分块弹窗 -->
    <div v-if="showChunksDialog" class="modal-overlay" @click.self="showChunksDialog = false">
      <div class="modal modal-lg">
        <div class="modal-header">
          <h3>分块管理 - {{ viewingDoc?.sourceFileName }}</h3>
          <button class="modal-close" @click="showChunksDialog = false">&times;</button>
        </div>
        <div class="modal-body">
          <div v-if="chunks.length === 0" class="loading-state">加载中...</div>
          <div v-else class="chunks-list">
            <div v-for="(chunk, idx) in chunks" :key="chunk.id" class="chunk-item">
              <div class="chunk-header">
                <span class="chunk-index">分块 #{{ idx + 1 }}</span>
                <button
                  v-if="editingChunkId !== chunk.id"
                  class="icon-btn"
                  @click="startEditChunk(chunk)"
                  title="编辑"
                >
                  <Pencil :size="14" />
                </button>
              </div>
              <div v-if="editingChunkId === chunk.id" class="chunk-edit">
                <textarea
                  v-model="editingChunkContent"
                  class="chunk-textarea"
                  rows="5"
                ></textarea>
                <div class="chunk-edit-actions">
                  <button class="btn btn-primary" @click="saveChunk(chunk)">保存</button>
                  <button class="btn btn-secondary" @click="cancelEditChunk">取消</button>
                </div>
              </div>
              <div v-else class="chunk-content">{{ chunk.content }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑知识库弹窗 -->
    <div v-if="showCreateDialog" class="modal-overlay" @click.self="showCreateDialog = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editingKb ? '编辑知识库' : '新建知识库' }}</h3>
          <button class="modal-close" @click="showCreateDialog = false">&times;</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>名称 *</label>
            <input v-model="kbForm.name" class="form-input" placeholder="知识库名称" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="kbForm.description" class="form-textarea" rows="3" placeholder="知识库描述"></textarea>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>检索类型</label>
              <select v-model="kbForm.searchType" class="form-select">
                <option value="hybrid">混合检索</option>
                <option value="similarity">向量检索</option>
                <option value="bm25">BM25 检索</option>
              </select>
            </div>
            <div class="form-group">
              <label>Top K</label>
              <input v-model.number="kbForm.topK" type="number" class="form-input" min="1" max="20" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>分块大小</label>
              <input v-model.number="kbForm.chunkSize" type="number" class="form-input" min="100" max="5000" />
            </div>
            <div class="form-group">
              <label>重叠大小</label>
              <input v-model.number="kbForm.chunkOverlap" type="number" class="form-input" min="0" max="1000" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>分数阈值</label>
              <input v-model.number="kbForm.scoreThreshold" type="number" class="form-input" min="0" max="1" step="0.05" />
            </div>
            <div class="form-group">
              <label>Embedding 维度</label>
              <input v-model.number="kbForm.embeddingDimensions" type="number" class="form-input" min="128" max="4096" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-group checkbox-group">
              <label>
                <input type="checkbox" v-model="kbForm.enableBM25" />
                启用 BM25 检索
              </label>
            </div>
            <div class="form-group checkbox-group">
              <label>
                <input type="checkbox" v-model="kbForm.enableReranker" />
                启用 Reranker 重排序
              </label>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="showCreateDialog = false">取消</button>
          <button class="btn btn-primary" @click="saveKb" :disabled="!kbForm.name">
            {{ editingKb ? '保存' : '创建' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, TransitionGroup } from 'vue'
import { knowledgeApi } from '@/services/api'
import type { KnowledgeBase as KB, Document, Chunk, SearchResult } from '@/services/api'
import {
  Plus, BookOpen, FileText, Grid3x3, Pencil, Trash2,
  ArrowLeft, Upload, Search
} from 'lucide-vue-next'

// ========== 状态 ==========
const loading = ref(false)
const kbs = ref<KB[]>([])
const selectedKb = ref<KB | null>(null)
const activeTab = ref<'documents' | 'settings' | 'search'>('documents')

// 文档
const documents = ref<Document[]>([])
const uploading = ref(false)

// 分块
const showChunksDialog = ref(false)
const viewingDoc = ref<Document | null>(null)
const chunks = ref<Chunk[]>([])
const editingChunkId = ref<string | null>(null)
const editingChunkContent = ref('')

// 搜索
const searchQuery = ref('')
const searchType = ref('hybrid')
const searchTopK = ref(5)
const searchResults = ref<SearchResult[]>([])
const searching = ref(false)
const searched = ref(false)
const searchFocused = ref(false)

const searchModes = [
  { value: 'hybrid', label: '混合检索', icon: '🔄' },
  { value: 'similarity', label: '向量检索', icon: '📐' },
  { value: 'bm25', label: '全文检索', icon: '📄' },
]

function searchTypeLabel(type: string) {
  const map: Record<string, string> = { hybrid: '混合检索', similarity: '向量检索', bm25: '全文检索', vector: '向量', fulltext: '全文' }
  return map[type] || type
}

function methodLabel(method: string) {
  const map: Record<string, string> = { hybrid: '混合', similarity: '向量', bm25: '全文' }
  return map[method] || method
}

// 创建/编辑
const showCreateDialog = ref(false)
const editingKb = ref<KB | null>(null)
const kbForm = ref({
  name: '',
  description: '',
  searchType: 'hybrid',
  topK: 5,
  chunkSize: 1000,
  chunkOverlap: 200,
  scoreThreshold: 0.7,
  embeddingDimensions: 1024,
  enableBM25: true,
  enableReranker: false,
})

// ========== 知识库设置（Dify 风格）==========
const kbSettings = ref({
  name: '',
  description: '',
  segmentationMode: 'general' as 'general' | 'parent-child' | 'qa',
  embeddingModel: 'nomic-embed-text:latest',
  rerankerModel: '',
  enableReranker: false,
  retrievalMethod: 'hybrid' as 'vector' | 'fulltext' | 'hybrid',
  topK: 5,
  scoreThreshold: 0.5,
})

// 权重配置
const vectorWeight = ref(50)
const fulltextWeight = computed(() => 100 - vectorWeight.value)

// BM25 关键词数量
const bm25KeywordCount = ref(10)

// ========== 分段配置（Dify 风格）==========
const chunkConfig = ref({
  separator: '\\n\\n',
  chunkSize: 1024,
  chunkOverlap: 50,
  removeExtraSpaces: true,
  removeUrls: false,
})

// 父子分段模式
const parentChunkMode = ref<'paragraph' | 'fulltext'>('paragraph')
const parentChunkSize = ref(1024)
const childChunkSize = ref(512)

// 预览分块
function previewChunks() {
  // TODO: 调用后端预览接口
  console.log('预览分块', { ...chunkConfig.value, parentChunkMode: parentChunkMode.value })
}

function resetChunkConfig() {
  chunkConfig.value = {
    separator: '\\n\\n',
    chunkSize: 1024,
    chunkOverlap: 50,
    removeExtraSpaces: true,
    removeUrls: false,
  }
  parentChunkSize.value = 1024
  childChunkSize.value = 512
}

// 加载知识库设置
function loadKbSettings() {
  if (!selectedKb.value) return
  kbSettings.value = {
    name: selectedKb.value.name,
    description: selectedKb.value.description || '',
    segmentationMode: 'general',
    embeddingModel: 'nomic-embed-text:latest',
    rerankerModel: 'bge-reranker-v2-m3',
    enableReranker: selectedKb.value.enableReranker || false,
    retrievalMethod: selectedKb.value.searchType || 'hybrid',
    topK: selectedKb.value.topK || 5,
    scoreThreshold: selectedKb.value.scoreThreshold || 0.5,
  }
}

// 保存知识库设置
async function saveKbSettings() {
  if (!selectedKb.value) return
  try {
    await knowledgeApi.update(selectedKb.value.id, {
      name: kbSettings.value.name,
      description: kbSettings.value.description,
      searchType: kbSettings.value.retrievalMethod,
      topK: kbSettings.value.topK,
      scoreThreshold: kbSettings.value.scoreThreshold,
      enableReranker: kbSettings.value.enableReranker,
      enableBM25: kbSettings.value.retrievalMethod !== 'similarity',
      chunkSize: kbForm.value.chunkSize,
      chunkOverlap: kbForm.value.chunkOverlap,
      embeddingDimensions: kbForm.value.embeddingDimensions,
    })
    // 更新本地数据
    selectedKb.value.name = kbSettings.value.name
    selectedKb.value.description = kbSettings.value.description
    alert('设置已保存')
  } catch (e) {
    console.error('Failed to save settings', e)
    alert('保存失败')
  }
}

const detailTabs = [
  { key: 'documents', label: '文档' },
  { key: 'settings', label: '设置' },
  { key: 'search', label: '检索测试' },
]

// ========== 生命周期 ==========
onMounted(() => {
  loadKbs()
})

// ========== 方法 ==========
async function loadKbs() {
  loading.value = true
  try {
    kbs.value = await knowledgeApi.list()
  } catch (e) {
    console.error('Failed to load knowledge bases', e)
  } finally {
    loading.value = false
  }
}

function selectKb(kb: KB) {
  selectedKb.value = kb
  activeTab.value = 'documents'
  loadDocuments()
  loadKbSettings()
}

async function loadDocuments() {
  if (!selectedKb.value) return
  try {
    documents.value = await knowledgeApi.listDocuments(selectedKb.value.id)
  } catch (e) {
    console.error('Failed to load documents', e)
  }
}

// ========== 创建/编辑知识库 ==========
function editKb(kb: KB) {
  editingKb.value = kb
  kbForm.value = {
    name: kb.name,
    description: kb.description || '',
    searchType: kb.searchType,
    topK: kb.topK,
    chunkSize: kb.chunkSize,
    chunkOverlap: kb.chunkOverlap,
    scoreThreshold: kb.scoreThreshold,
    embeddingDimensions: kb.embeddingDimensions,
    enableBM25: kb.enableBM25,
    enableReranker: kb.enableReranker,
  }
  showCreateDialog.value = true
}

async function saveKb() {
  try {
    if (editingKb.value) {
      await knowledgeApi.update(editingKb.value.id, kbForm.value)
    } else {
      await knowledgeApi.create(kbForm.value)
    }
    showCreateDialog.value = false
    editingKb.value = null
    resetForm()
    await loadKbs()
  } catch (e) {
    console.error('Failed to save knowledge base', e)
  }
}

async function confirmDeleteKb(kb: KB) {
  if (!confirm(`确定删除知识库 "${kb.name}"？所有文档和向量数据将被删除。`)) return
  try {
    await knowledgeApi.delete(kb.id)
    await loadKbs()
  } catch (e) {
    console.error('Failed to delete knowledge base', e)
  }
}

function resetForm() {
  kbForm.value = {
    name: '',
    description: '',
    searchType: 'hybrid',
    topK: 5,
    chunkSize: 1000,
    chunkOverlap: 200,
    scoreThreshold: 0.7,
    embeddingDimensions: 1024,
    enableBM25: true,
    enableReranker: false,
  }
}

// ========== 文档上传 ==========
async function onFileUpload(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !selectedKb.value) return

  if (file.size > 50 * 1024 * 1024) {
    alert('文件大小不能超过 50MB')
    return
  }

  uploading.value = true
  try {
    await knowledgeApi.uploadDocument(selectedKb.value.id, file)
    // 轮询等待索引完成
    setTimeout(() => loadDocuments(), 2000)
  } catch (e) {
    console.error('Failed to upload document', e)
    alert('上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

async function confirmDeleteDoc(doc: Document) {
  if (!confirm(`确定删除文档 "${doc.sourceFileName}"？`)) return
  if (!selectedKb.value) return
  try {
    await knowledgeApi.deleteDocument(selectedKb.value.id, doc.id)
    await loadDocuments()
  } catch (e) {
    console.error('Failed to delete document', e)
  }
}

// ========== 分块查看 ==========
async function viewChunks(doc: Document) {
  viewingDoc.value = doc
  showChunksDialog.value = true
  if (!selectedKb.value) return
  try {
    chunks.value = await knowledgeApi.listChunks(selectedKb.value.id, doc.id)
  } catch (e) {
    console.error('Failed to load chunks', e)
  }
}

function startEditChunk(chunk: Chunk) {
  editingChunkId.value = chunk.id
  editingChunkContent.value = chunk.content
}

function cancelEditChunk() {
  editingChunkId.value = null
  editingChunkContent.value = ''
}

async function saveChunk(chunk: Chunk) {
  if (!selectedKb.value || !viewingDoc.value) return
  try {
    await knowledgeApi.updateChunk(
      selectedKb.value.id,
      viewingDoc.value.id,
      chunk.id,
      editingChunkContent.value
    )
    chunk.content = editingChunkContent.value
    editingChunkId.value = null
    editingChunkContent.value = ''
  } catch (e) {
    console.error('Failed to update chunk', e)
  }
}

// ========== 检索 ==========
async function doSearch() {
  if (!selectedKb.value || !searchQuery.value.trim()) return
  searching.value = true
  searched.value = true
  try {
    searchResults.value = await knowledgeApi.search(
      selectedKb.value.id,
      searchQuery.value,
      searchTopK.value,
      searchType.value
    )
  } catch (e) {
    console.error('Search failed', e)
  } finally {
    searching.value = false
  }
}

// ========== 工具函数 ==========
function statusLabel(status: string): string {
  const map: Record<string, string> = {
    pending: '待处理',
    indexing: '索引中',
    completed: '已完成',
    error: '失败',
  }
  return map[status] || status
}

function fileIcon(type: string): string {
  const map: Record<string, string> = {
    txt: '📝',
    md: '📋',
    pdf: '📕',
    docx: '📘',
    doc: '📘',
    xlsx: '📊',
  }
  return map[type] || '📄'
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
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

/* 按钮 */
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
  gap: 6px;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #1a1a2e;
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: #2d2d44;
}

.btn-secondary {
  background: #f3f4f6;
  color: #374151;
}

.btn-secondary:hover {
  background: #e5e7eb;
}

.icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  transition: all 0.2s;
}

.icon-btn:hover {
  background: #f3f4f6;
  color: #1f2937;
}

.icon-btn.danger:hover {
  background: #fef2f2;
  color: #dc2626;
}

/* ========== 知识库卡片 ========== */
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.kb-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  border: 1px solid #e5e7eb;
  transition: all 0.2s;
}

.kb-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
}

.kb-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.kb-icon {
  width: 40px;
  height: 40px;
  background: #eff6ff;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3b82f6;
}

.kb-actions {
  display: flex;
  gap: 4px;
}

.kb-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.kb-desc {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.4;
}

.kb-card-footer {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #9ca3af;
}

.kb-stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.kb-badge {
  margin-left: auto;
  padding: 2px 8px;
  background: #f3f4f6;
  border-radius: 4px;
  color: #6b7280;
  font-size: 11px;
}

/* ========== 详情页 ========== */
.kb-detail {
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  max-width: 960px;
  margin: 0 auto;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e7eb;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
}

.back-btn:hover {
  background: #f3f4f6;
  color: #1f2937;
}

.detail-tabs {
  display: flex;
  gap: 4px;
  background: #f3f4f6;
  border-radius: 8px;
  padding: 3px;
}

.tab-btn {
  padding: 6px 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background: #ffffff;
  color: #1f2937;
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

/* ========== 文档列表 ========== */
.documents-section, .search-section {
  padding: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h2 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 8px;
  gap: 16px;
}

.doc-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.doc-icon {
  font-size: 20px;
}

.doc-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.doc-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}

.doc-size {
  font-size: 12px;
  color: #9ca3af;
}

.status-tag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag.pending {
  background: #fef3c7;
  color: #92400e;
}

.status-tag.indexing {
  background: #dbeafe;
  color: #1e40af;
}

.status-tag.completed {
  background: #d1fae5;
  color: #065f46;
}

.status-tag.error {
  background: #fee2e2;
  color: #991b1b;
}

.doc-actions {
  display: flex;
  gap: 4px;
}

/* ========== 检索测试（重新设计） ========== */
.search-section {
  padding: 24px;
}

/* 搜索 Hero 区域 */
.search-hero {
  text-align: center;
  margin-bottom: 28px;
}

.search-hero-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  color: #3b82f6;
  margin-bottom: 14px;
}

.search-hero-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 4px;
}

.search-hero-desc {
  font-size: 13.5px;
  color: #6b7280;
  margin: 0 0 22px;
}

/* 搜索输入框容器 */
.search-input-wrap {
  max-width: 640px;
  margin: 0 auto;
}

.search-input-inner {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 5px 5px 5px 16px;
  background: #fff;
  border: 2px solid #e5e7eb;
  border-radius: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 8px 24px rgba(0,0,0,0.04);
  transition: all 0.2s ease;
}

.search-input-wrap.focused .search-input-inner,
.search-input-inner:focus-within {
  border-color: #3b82f6;
  box-shadow: 0 1px 3px rgba(59,130,246,0.08), 0 12px 32px rgba(59,130,246,0.10);
}

.search-icon {
  color: #9ca3af;
  flex-shrink: 0;
  transition: color 0.15s;
}

.search-input-wrap.focused .search-icon {
  color: #3b82f6;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  padding: 10px 12px;
  font-size: 14.5px;
  color: #1f2937;
  background: transparent;
  min-width: 0;
}

.search-input::placeholder {
  color: #c4c9d0;
}

.btn-search {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 20px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.18s;
  white-space: nowrap;
}

.btn-search:hover:not(:disabled) {
  opacity: 0.92;
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(59,130,246,0.35);
}

.btn-search:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
}

/* 模式选择栏 */
.search-mode-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.mode-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 1.5px solid #e5e7eb;
  border-radius: 20px;
  background: #fff;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.17s;
  user-select: none;
}

.mode-chip:hover {
  border-color: #93c5fd;
  color: #374151;
  background: #fafbff;
}

.mode-chip.active {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 600;
  box-shadow: 0 1px 6px rgba(59,130,246,0.12);
}

.mode-icon {
  font-size: 14px;
  line-height: 1;
}

.mode-divider {
  width: 1px;
  height: 20px;
  background: #e5e7eb;
  margin: 0 4px;
}

.topk-select {
  padding: 6px 12px;
  border: 1.5px solid #e5e7eb;
  border-radius: 20px;
  font-size: 13px;
  color: #6b7280;
  outline: none;
  background: #fff;
  cursor: pointer;
  transition: all 0.17s;
}

.topk-select:focus {
  border-color: #3b82f6;
}

/* ====== 结果区域 ====== */
.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px 12px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 16px;
}

.results-count {
  font-size: 13.5px;
  color: #6b7280;
}

.results-count strong {
  color: #2563eb;
  font-weight: 700;
}

.results-meta {
  font-size: 12px;
  color: #9ca3af;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 结果卡片 */
.result-card {
  display: flex;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 14px;
  transition: all 0.2s;
  position: relative;
}

.result-card:hover {
  border-color: #dce4ed;
  box-shadow: 0 4px 16px rgba(0,0,0,0.05);
  transform: translateY(-1px);
}

.result-card-left {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  padding-top: 2px;
}

.result-rank-badge {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  color: #fff;
  flex-shrink: 0;
}

.result-rank-badge.rank-1 { background: linear-gradient(135deg, #f59e0b, #d97706); }
.result-rank-badge.rank-2 { background: linear-gradient(135deg, #94a3b8, #64748b); }
.result-rank-badge.rank-3 { background: linear-gradient(135deg, #cd7c3a, #a16207); }
.result-rank-badge:not(.rank-1):not(.rank-2):not(.rank-3) {
  background: #f1f5f9; color: #94a3b8; font-weight: 700;
}

.result-card-body {
  flex: 1;
  min-width: 0;
}

.result-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.result-source {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #9ca3af;
}

.result-source svg {
  opacity: 0.6;
}

/* 分数进度条 */
.result-score-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.score-track {
  width: 72px;
  height: 6px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
}

.score-fill {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #3b82f6, #60a5fa);
  transition: width 0.5s ease;
}

.score-text {
  font-size: 12.5px;
  font-weight: 700;
  color: #2563eb;
  min-width: 44px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.result-text {
  font-size: 14px;
  color: #374151;
  line-height: 1.65;
  margin: 0 0 10px;
  word-break: break-word;
}

/* 标签 */
.result-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 6px;
  font-size: 11.5px;
  font-weight: 500;
  background: #f3f4f6;
  color: #6b7280;
}

.tag-method.tag-hybrid {
  background: #ede9fe; color: #7c3aed;
}
.tag-method.tag-similarity {
  background: #dbeafe; color: #2563eb;
}
.tag-method.tag-bm25 {
  background: #fef3c7; color: #92400e;
}

/* ====== 空状态 ====== */
.empty-state-search {
  text-align: center;
  padding: 48px 20px;
}

.empty-icon-lg {
  font-size: 44px;
  margin-bottom: 12px;
  filter: grayscale(0.3);
}

.empty-state-search h4 {
  font-size: 16px;
  color: #374151;
  margin: 0 0 6px;
}

.empty-state-search p {
  font-size: 13px;
  color: #9ca3af;
  margin: 0 0 18px;
}

.btn-outline {
  padding: 7px 18px;
  border: 1.5px solid #d1d5db;
  border-radius: 8px;
  background: transparent;
  color: #4b5563;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-outline:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #f8faff;
}

/* 初始提示卡片 */
.empty-hint {
  margin-top: 12px;
}

.hint-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  max-width: 720px;
  margin: 0 auto;
}

.hint-card {
  position: relative;
  padding: 20px 18px;
  background: #fafbfc;
  border: 1.5px solid #eef0f3;
  border-radius: 14px;
  text-align: left;
  transition: all 0.2s;
}

.hint-card:hover {
  border-color: #dce4ed;
  box-shadow: 0 2px 10px rgba(0,0,0,0.04);
  transform: translateY(-2px);
}

.hint-card-active {
  background: linear-gradient(135deg, #eff6ff, #fdf4ff);
  border-color: #bfdbfe;
}

.hint-card-icon {
  font-size: 26px;
  margin-bottom: 10px;
}

.hint-card-title {
  font-size: 14px;
  font-weight: 650;
  color: #1f2937;
  margin-bottom: 5px;
}

.hint-card-desc {
  font-size: 12.5px;
  color: #6b7280;
  line-height: 1.55;
}

.badge-rec {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background: #dcfce7;
  color: #166534;
}

/* 加载 spinner */
.spinner-sm {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 结果动画 */
.result-enter-active {
  animation: resultIn 0.35s ease-out;
}

.result-leave-active {
  animation: resultOut 0.25s ease-in;
}

@keyframes resultIn {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes resultOut {
  from { opacity: 1; transform: translateY(0); }
  to   { opacity: 0; transform: translateY(-8px); }
}

/* ========== 弹窗 ========== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: #ffffff;
  border-radius: 16px;
  width: 520px;
  max-height: 80vh;
  overflow: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.modal-lg {
  width: 720px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.modal-close {
  background: none;
  border: none;
  font-size: 24px;
  color: #9ca3af;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.modal-body {
  padding: 20px 24px;
}

.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* ========== 表单 ========== */
.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

.form-input, .form-textarea, .form-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.form-input:focus, .form-textarea:focus, .form-select:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-textarea {
  resize: vertical;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.checkbox-group label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

/* ========== 分块 ========== */
.chunks-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-item {
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}

.chunk-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.chunk-index {
  font-size: 12px;
  font-weight: 600;
  color: #3b82f6;
}

.chunk-content {
  font-size: 13px;
  color: #374151;
  line-height: 1.5;
  white-space: pre-wrap;
}

.chunk-textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid #3b82f6;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
}

.chunk-edit-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  justify-content: flex-end;
}

/* ========== 空状态 ========== */
.empty-state {
  background: #ffffff;
  border-radius: 16px;
  padding: 80px 40px;
  text-align: center;
}

.empty-state.small {
  padding: 40px;
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

.loading-state {
  text-align: center;
  padding: 40px;
  color: #9ca3af;
  font-size: 14px;
}

.upload-btn {
  cursor: pointer;
}

/* ========== Dify 风格设置页面样式 ========== */
.settings-section {
  max-width: 860px;
  margin: 0 auto;
}

.section-header {
  margin-bottom: 24px;
}

.section-header h2 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.section-hint {
  font-size: 13px;
  color: #6b7280;
}

.dify-settings-form {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.setting-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.setting-label {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
}

.help-link {
  font-size: 12px;
  font-weight: 400;
  color: #3b82f6;
  text-decoration: none;
}

.help-link:hover {
  text-decoration: underline;
}

.setting-field {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px 16px;
}

.field-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-icon {
  font-size: 18px;
  width: 32px;
  height: 32px;
  background: #fff;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e5e7eb;
}

.flex-1 { flex: 1; }
.mt-2 { margin-top: 8px; }
.mt-3 { margin-top: 16px; }

/* ====== 模式卡片（分段/检索）====== */
.mode-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.mode-card {
  position: relative;
  padding: 16px 14px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.mode-card:hover {
  border-color: #93c5fd;
}

.mode-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.mode-card .mode-icon {
  font-size: 22px;
  margin-bottom: 8px;
}

.mode-card .mode-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.mode-card .mode-desc {
  font-size: 11px;
  color: #6b7280;
  line-height: 1.5;
}

/* 有展开配置时，卡片紧凑 */
.mode-cards.has-expand {
  gap: 8px;
}

.mode-cards.has-expand .mode-card {
  padding: 12px 10px;
}

.mode-cards.has-expand .mode-card .mode-icon {
  font-size: 18px;
}

/* ====== 分段配置面板（Dify 风格）====== */
.seg-config-panel {
  margin-top: 12px;
  border: 2px solid #3b82f6;
  border-radius: 12px;
  padding: 20px;
  background: #fff;
  animation: slideDown 0.25s ease-out;
}

.seg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e5e7eb;
  margin-bottom: 16px;
}

.seg-header-icon { font-size: 18px; }
.seg-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.seg-header-desc {
  font-size: 11px;
  color: #9ca3af;
  margin-left: auto;
}

/* 三列/两列布局 */
.seg-row-3, .seg-row-2 {
  display: grid;
  gap: 16px;
}
.seg-row-3 { grid-template-columns: repeat(3, 1fr); }
.seg-row-2 { grid-template-columns: repeat(2, 1fr); }

.seg-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.seg-field-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
}

.seg-label, .seg-subtitle {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}
.seg-label-xs {
  font-size: 11px;
  font-weight: 500;
  color: #6b7280;
  white-space: nowrap;
}
.seg-label .info-icon, .seg-label-xs .info-icon {
  cursor: help;
  opacity: 0.4;
}

/* 输入框 + 单位 */
.input-with-unit {
  display: flex;
  align-items: center;
  position: relative;
}
.input-with-unit input {
  flex: 1;
  padding-right: 70px;
}
.input-with-unit .unit,
.input-with-unit .unit-xs {
  position: absolute;
  right: 10px;
  font-size: 11px;
  color: #9ca3af;
  pointer-events: none;
}
.input-with-unit .unit-xs { font-size: 10px; }

.form-input-sm {
  height: 34px;
  font-size: 13px;
  padding: 6px 10px;
}
.form-input-xs {
  height: 28px;
  font-size: 11px;
  padding: 4px 8px;
}

/* 文本预处理规则 */
.preprocess-rules {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f3f4f6;
}
.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  margin-top: 8px;
}
.check-item input[type="checkbox"] {
  width: 15px;
  height: 15px;
  accent-color: #3b82f6;
}

/* 操作按钮 */
.seg-actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #f3f4f6;
}
.btn-preview {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  background: #eff6ff;
  color: #3b82f6;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-preview:hover {
  background: #dbeafe;
  border-color: #93c5fd;
}
.btn-reset {
  padding: 7px 14px;
  background: transparent;
  color: #6b7280;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-reset:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}

/* ====== 父子分段：父块模式选择 ===== */
.seg-subsection {
  /* empty */
}
.parent-mode-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
}
.pmode-card {
  display: flex;
  gap: 12px;
  padding: 14px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}
.pmode-card:hover {
  border-color: #93c5fd;
}
.pmode-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
}
.pmode-radio {
  width: 18px;
  height: 18px;
  border: 2px solid #d1d5db;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 2px;
  transition: all 0.2s;
  box-sizing: border-box;
}
.pmode-radio.checked {
  border-color: #3b82f6;
  background: #fff;
  box-shadow: inset 0 0 0 5px #3b82f6;
}
.pmode-content { flex: 1; }
.pmode-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}
.pmode-desc {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
}
.pmode-fields {
  display: flex;
  gap: 20px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e5e7eb;
}

/* 推荐标签 */
.mode-badge,
.method-badge {
  position: absolute;
  top: -8px;
  right: -6px;
  background: #22c55e;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

/* 滑块 */
.field-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label-sm {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.slider-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.range-slider {
  flex: 1;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: #e5e7eb;
  border-radius: 2px;
  outline: none;
}

.range-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  background: #3b82f6;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.range-value {
  font-size: 14px;
  font-weight: 600;
  color: #3b82f6;
  min-width: 28px;
  text-align: right;
}

/* ====== 检索方法卡片 ====== */
.retrieval-methods {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.method-card {
  position: relative;
  padding: 16px 18px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.method-card:hover {
  border-color: #c7d2fe;
}

.method-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.method-icon {
  font-size: 20px;
  margin-bottom: 6px;
}

.method-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}

.method-desc {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

/* 子卡片 (Rerank) */
.method-subcard {
  padding: 14px 18px;
  background: #fafbfc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-left: 20px;
}

.method-subheader {
  margin-bottom: 10px;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #3b82f6;
}

.method-fields {
  display: flex;
  gap: 24px;
  padding-top: 10px;
  border-top: 1px solid #e5e7eb;
}

.field-inline {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label-xs {
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
}

.form-input-xs {
  width: 80px;
  padding: 6px 10px;
  font-size: 13px;
}

/* ====== 保存按钮 ====== */
.settings-footer {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #e5e7eb;
}

.btn-save-primary {
  padding: 10px 32px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(59,130,246,0.25);
}

.btn-save-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59,130,246,0.35);
}

/* ========== 混合检索展开样式 ========== */
.method-card-hybrid {
  padding-bottom: 16px;
}

/* 方法卡片展开配置（通用） */
.method-expand-config {
  margin-top: 14px;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  animation: slideDown 0.2s ease-out;
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-6px); }
  to { opacity: 1; transform: translateY(0); }
}

.config-fields {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.form-select-sm {
  max-width: 280px;
  padding: 8px 12px;
  font-size: 13px;
}

.hybrid-options {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.hybrid-sub-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.sub-option-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.sub-option-card:hover {
  border-color: #c7d2fe;
}

.sub-option-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.sub-option-icon {
  font-size: 20px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f9fafb;
  border-radius: 8px;
  flex-shrink: 0;
}

.sub-option-content {
  flex: 1;
  min-width: 0;
}

.sub-option-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.sub-option-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.radio-dot {
  width: 18px;
  height: 18px;
  border: 2px solid #d1d5db;
  border-radius: 50%;
  position: relative;
  flex-shrink: 0;
  transition: all 0.15s;
}

.radio-dot.checked {
  border-color: #3b82f6;
}

.radio-dot.checked::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  background: #3b82f6;
  border-radius: 50%;
}

.sub-option-desc {
  font-size: 11px;
  color: #6b7280;
  line-height: 1.5;
  margin: 0;
}

/* Rerank 配置 */
.rerank-config {
  margin-top: 14px;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.rerank-select {
  width: 100%;
  margin-bottom: 12px;
}

.rerank-fields {
  display: flex;
  gap: 24px;
}

.rerank-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-input-sm {
  width: 100px;
  padding: 7px 10px;
  font-size: 13px;
}

/* 权重配置 */
.weight-config {
  margin-top: 14px;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.weight-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.weight-row:last-child {
  margin-bottom: 0;
}

.weight-slider-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}

.range-sm {
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: #e5e7eb;
  border-radius: 2px;
  outline: none;
  flex: 1;
}

.range-sm::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  background: #3b82f6;
  border-radius: 50%;
  cursor: pointer;
}

.range-sm[readonly] {
  opacity: 0.5;
  cursor: not-allowed;
}

.weight-value {
  font-size: 13px;
  font-weight: 600;
  color: #3b82f6;
  min-width: 40px;
  text-align: right;
}
</style>