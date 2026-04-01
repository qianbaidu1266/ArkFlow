<template>
  <div class="code-node-config">
    <!-- 输入变量 -->
    <div class="config-section">
      <div class="section-header">
        <label class="section-label">输入变量</label>
        <button class="btn-add-small" @click="addInputVariable">+</button>
      </div>
      <div class="variables-list">
        <div 
          v-for="(variable, index) in inputVariables" 
          :key="index"
          class="variable-row"
        >
          <input
            v-model="variable.name"
            type="text"
            class="var-input"
            placeholder="变量名"
            @change="updateConfig"
          />
          <span class="arrow">→</span>
          <div class="var-select-wrapper">
            <select
              v-model="variable.source"
              class="var-select"
              @change="updateConfig"
            >
              <option value="">选择变量</option>
              <optgroup label="系统变量">
                <option value="sys.user_id">sys.user_id</option>
                <option value="sys.app_id">sys.app_id</option>
                <option value="sys.workflow_id">sys.workflow_id</option>
                <option value="sys.execution_id">sys.execution_id</option>
                <option value="sys.timestamp">sys.timestamp</option>
              </optgroup>
              <optgroup label="节点输出">
                <option 
                  v-for="output in availableOutputs" 
                  :key="output.key"
                  :value="output.key"
                >
                  {{ output.nodeName }}: {{ output.key }}
                </option>
              </optgroup>
            </select>
          </div>
          <button class="btn-remove-small" @click="removeInputVariable(index)">×</button>
        </div>
      </div>
    </div>

    <!-- 编程语言 -->
    <div class="config-section">
      <label class="section-label">编程语言</label>
      <select
        v-model="config.language"
        class="config-select"
        @change="updateConfig"
      >
        <option value="javascript">JavaScript</option>
        <option value="python">Python</option>
        <option value="java">Java</option>
      </select>
    </div>

    <!-- 代码编辑器 -->
    <div class="config-section">
      <label class="section-label">代码</label>
      <div class="code-hint">
        定义 main() 函数，参数为输入变量，返回对象包含输出字段
      </div>
      <textarea
        v-model="config.code"
        class="code-editor"
        rows="12"
        :placeholder="codePlaceholder"
        @change="updateConfig"
      ></textarea>
    </div>

    <!-- 输出变量 -->
    <div class="config-section">
      <div class="section-header">
        <label class="section-label">输出变量</label>
        <button class="btn-add-small" @click="addOutputVariable">+</button>
      </div>
      <div class="variables-list">
        <div 
          v-for="(variable, index) in outputVariables" 
          :key="index"
          class="variable-row"
        >
          <input
            v-model="variable.name"
            type="text"
            class="var-input"
            placeholder="字段名"
            @change="updateConfig"
          />
          <span class="arrow">→</span>
          <input
            v-model="variable.target"
            type="text"
            class="var-input"
            placeholder="变量名"
            @change="updateConfig"
          />
          <select
            v-model="variable.type"
            class="var-type-select"
            @change="updateConfig"
          >
            <option value="String">String</option>
            <option value="Number">Number</option>
            <option value="Boolean">Boolean</option>
            <option value="Object">Object</option>
            <option value="Array">Array</option>
          </select>
          <button class="btn-remove-small" @click="removeOutputVariable(index)">×</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useWorkflowStore } from '@/stores/workflow'

const props = defineProps<{
  nodeId: string
  initialConfig: Record<string, any>
}>()

const emit = defineEmits<{
  update: [config: Record<string, any>]
}>()

const workflowStore = useWorkflowStore()

const config = ref({
  language: 'javascript',
  code: '',
  inputVariables: [],
  outputVariables: [],
  ...props.initialConfig
})

const inputVariables = computed({
  get: () => config.value.inputVariables || [],
  set: (val) => { config.value.inputVariables = val }
})

const outputVariables = computed({
  get: () => config.value.outputVariables || [],
  set: (val) => { config.value.outputVariables = val }
})

// 获取可用的前置节点输出
const availableOutputs = computed(() => {
  const outputs: Array<{ key: string; nodeName: string; nodeId: string }> = []
  const currentNode = workflowStore.nodes[props.nodeId]
  
  if (!currentNode) return outputs
  
  // 遍历所有节点，找到在当前节点之前的节点
  Object.entries(workflowStore.nodes).forEach(([id, node]) => {
    if (id === props.nodeId) return
    
    // 获取节点的输出参数定义
    const nodeOutputs = getNodeOutputs(node)
    nodeOutputs.forEach(output => {
      outputs.push({
        key: output.key,
        nodeName: node.name || node.type,
        nodeId: id
      })
    })
  })
  
  return outputs
})

// 获取节点的输出参数
function getNodeOutputs(node: any): Array<{ key: string; type: string }> {
  const outputs: Array<{ key: string; type: string }> = []
  
  switch (node.type) {
    case 'START':
      outputs.push({ key: 'input', type: 'Object' })
      break
    case 'LLM':
      outputs.push({ key: node.config?.outputKey || 'llm_output', type: 'String' })
      break
    case 'CODE':
      if (node.config?.outputVariables) {
        node.config.outputVariables.forEach((v: any) => {
          outputs.push({ key: v.target || v.name, type: v.type || 'String' })
        })
      } else {
        outputs.push({ key: 'code_result', type: 'Object' })
      }
      break
    case 'HTTP':
      outputs.push({ key: node.config?.outputKey || 'http_response', type: 'Object' })
      break
    case 'KNOWLEDGE_RETRIEVAL':
      outputs.push({ key: node.config?.outputKey || 'retrieved_context', type: 'Array' })
      break
    default:
      outputs.push({ key: `${node.type.toLowerCase()}_output`, type: 'Object' })
  }
  
  return outputs
}

const codePlaceholder = computed(() => {
  const vars = inputVariables.value.map(v => v.name).filter(Boolean).join(', ')
  
  switch (config.value.language) {
    case 'java':
      return `public static Object main(${vars ? vars.split(', ').map(() => 'Object').join(', ') : ''}) {
    Map<String, Object> result = new HashMap<>();
    result.put("output1", "value1");
    return result;
}`
    case 'python':
      return `def main(${vars || ''}):
    return {
        "output1": "value1"
    }`
    default:
      return `function main(${vars || ''}) {
    return {
        output1: "value1"
    };
}`
  }
})

function addInputVariable() {
  inputVariables.value.push({ name: '', source: '' })
  updateConfig()
}

function removeInputVariable(index: number) {
  inputVariables.value.splice(index, 1)
  updateConfig()
}

function addOutputVariable() {
  outputVariables.value.push({ name: '', target: '', type: 'String' })
  updateConfig()
}

function removeOutputVariable(index: number) {
  outputVariables.value.splice(index, 1)
  updateConfig()
}

function updateConfig() {
  emit('update', { ...config.value })
}

watch(() => props.initialConfig, (newConfig) => {
  config.value = {
    language: 'javascript',
    code: '',
    inputVariables: [],
    outputVariables: [],
    ...newConfig
  }
}, { deep: true })
</script>

<style scoped>
.code-node-config {
  padding: 0;
}

.config-section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.section-label {
  font-size: 12px;
  font-weight: 500;
  color: #374151;
}

.btn-add-small {
  width: 24px;
  height: 24px;
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.btn-add-small:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
}

.variables-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.variable-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.var-input {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 12px;
}

.var-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.var-select-wrapper {
  flex: 1.5;
}

.var-select {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 12px;
  background: #fff;
  cursor: pointer;
}

.var-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.var-type-select {
  width: 80px;
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 12px;
  background: #fff;
}

.arrow {
  color: #9ca3af;
  font-size: 12px;
}

.btn-remove-small {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.2s;
}

.btn-remove-small:hover {
  color: #ef4444;
}

.config-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
}

.config-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.code-hint {
  font-size: 11px;
  color: #6b7280;
  margin-bottom: 8px;
  padding: 8px;
  background: #f3f4f6;
  border-radius: 4px;
}

.code-editor {
  width: 100%;
  padding: 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  resize: vertical;
  min-height: 150px;
  background: #fafafa;
}

.code-editor:focus {
  outline: none;
  border-color: #3b82f6;
  background: #fff;
}
</style>
