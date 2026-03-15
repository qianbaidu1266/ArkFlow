<template>
  <div class="execution-panel">
    <div class="panel-header">
      <span class="panel-title">执行结果</span>
      <button class="btn-close" @click="close">×</button>
    </div>
    <div class="panel-content">
      <div v-if="!executionResult" class="empty-state">
        点击"运行"按钮执行工作流
      </div>
      <div v-else>
        <div class="result-status" :class="{ success: executionResult.success }">
          <span class="status-icon">{{ executionResult.success ? '✓' : '✗' }}</span>
          <span class="status-text">
            {{ executionResult.success ? '执行成功' : '执行失败' }}
          </span>
          <span class="duration">{{ executionResult.duration }}ms</span>
        </div>
        
        <div v-if="executionResult.error" class="error-message">
          {{ executionResult.error }}
        </div>
        
        <div v-if="executionResult.output" class="output-section">
          <div class="section-title">输出结果</div>
          <pre class="output-content">{{ formatOutput(executionResult.output) }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ExecutionResult } from '@/types/workflow'

const executionResult = ref<ExecutionResult | null>(null)

function setResult(result: ExecutionResult) {
  executionResult.value = result
}

function close() {
  executionResult.value = null
}

function formatOutput(output: any): string {
  if (typeof output === 'string') {
    return output
  }
  return JSON.stringify(output, null, 2)
}

// 暴露方法给父组件
defineExpose({
  setResult
})
</script>

<style scoped>
.execution-panel {
  position: fixed;
  bottom: 0;
  left: 280px;
  right: 320px;
  height: 300px;
  background: white;
  border-top: 1px solid #e2e8f0;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  z-index: 100;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.btn-close {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  
  &:hover {
    background: #e2e8f0;
    color: #64748b;
  }
}

.panel-content {
  flex: 1;
  overflow: auto;
  padding: 16px;
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: #94a3b8;
}

.result-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fee2e2;
  border-radius: 8px;
  margin-bottom: 16px;
  
  &.success {
    background: #d1fae5;
  }
  
  .status-icon {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: #ef4444;
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: bold;
  }
  
  &.success .status-icon {
    background: #10b981;
  }
  
  .status-text {
    flex: 1;
    font-size: 14px;
    font-weight: 500;
    color: #1e293b;
  }
  
  .duration {
    font-size: 12px;
    color: #64748b;
  }
}

.error-message {
  padding: 12px 16px;
  background: #fee2e2;
  border-radius: 8px;
  color: #ef4444;
  font-size: 13px;
  margin-bottom: 16px;
}

.output-section {
  .section-title {
    font-size: 12px;
    font-weight: 600;
    color: #64748b;
    text-transform: uppercase;
    margin-bottom: 8px;
  }
  
  .output-content {
    padding: 12px 16px;
    background: #f8fafc;
    border-radius: 8px;
    font-size: 13px;
    color: #1e293b;
    overflow: auto;
    max-height: 150px;
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
