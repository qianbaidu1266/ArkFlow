<template>
  <div class="start-node-config">
    <div class="config-section">
      <div class="section-header">
        <label class="section-label">输入字段</label>
        <span class="section-hint">定义工作流的输入参数</span>
      </div>
      
      <div class="variables-list">
        <div 
          v-for="(variable, index) in inputVariables" 
          :key="index"
          class="variable-card"
        >
          <div class="variable-row">
            <div class="field-group">
              <label class="field-label">字段名</label>
              <input
                v-model="variable.name"
                type="text"
                class="field-input"
                placeholder="如: query"
                @change="updateConfig"
              />
            </div>
            <div class="field-group type-field">
              <label class="field-label">类型</label>
              <select
                v-model="variable.type"
                class="field-select"
                @change="updateConfig"
              >
                <option value="string">String</option>
                <option value="number">Number</option>
                <option value="boolean">Boolean</option>
                <option value="object">Object</option>
                <option value="array">Array</option>
              </select>
            </div>
          </div>
          
          <div class="variable-row">
            <div class="field-group">
              <label class="field-label">描述</label>
              <input
                v-model="variable.description"
                type="text"
                class="field-input"
                placeholder="字段说明"
                @change="updateConfig"
              />
            </div>
          </div>
          
          <div class="variable-row">
            <div class="field-group checkbox-field">
              <label class="checkbox-label">
                <input
                  v-model="variable.required"
                  type="checkbox"
                  @change="updateConfig"
                />
                必填
              </label>
            </div>
            <div class="field-group">
              <label class="field-label">默认值</label>
              <input
                v-model="variable.defaultValue"
                type="text"
                class="field-input"
                placeholder="可选"
                @change="updateConfig"
              />
            </div>
          </div>
          
          <button class="btn-remove" @click="removeVariable(index)">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"/>
            </svg>
          </button>
        </div>
        
        <button class="btn-add" @click="addVariable">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14"/>
          </svg>
          添加输入字段
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  initialConfig: Record<string, any>
}>()

const emit = defineEmits<{
  update: [config: Record<string, any>]
}>()

const config = ref({
  inputVariables: [],
  ...props.initialConfig
})

const inputVariables = computed({
  get: () => config.value.inputVariables || [],
  set: (val) => { config.value.inputVariables = val }
})

function addVariable() {
  inputVariables.value.push({
    name: '',
    type: 'string',
    description: '',
    required: false,
    defaultValue: ''
  })
  updateConfig()
}

function removeVariable(index: number) {
  inputVariables.value.splice(index, 1)
  updateConfig()
}

function updateConfig() {
  emit('update', { ...config.value })
}

watch(() => props.initialConfig, (newConfig) => {
  config.value = {
    inputVariables: [],
    ...newConfig
  }
}, { deep: true })
</script>

<style scoped>
.start-node-config {
  padding: 0;
}

.config-section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.section-hint {
  font-size: 11px;
  color: #9ca3af;
}

.variables-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.variable-card {
  position: relative;
  padding: 16px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.variable-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.variable-row:last-child {
  margin-bottom: 0;
}

.field-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.type-field {
  flex: 0 0 100px;
}

.checkbox-field {
  flex: 0 0 80px;
  justify-content: center;
}

.field-label {
  font-size: 11px;
  font-weight: 500;
  color: #6b7280;
}

.field-input {
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
}

.field-input:focus {
  outline: none;
  border-color: #3b82f6;
}

.field-select {
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
}

.field-select:focus {
  outline: none;
  border-color: #3b82f6;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #374151;
  cursor: pointer;
  padding-top: 20px;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.btn-remove {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s;
}

.btn-remove:hover {
  background: #fee2e2;
  color: #ef4444;
}

.btn-add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  border: 1px dashed #d1d5db;
  background: #fff;
  border-radius: 6px;
  font-size: 13px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-add:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #eff6ff;
}
</style>
