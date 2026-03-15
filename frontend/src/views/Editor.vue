<template>
  <div class="editor">
    <!-- 工具栏 -->
    <EditorToolbar />
    
    <div class="editor-body">
      <!-- 左侧节点面板 -->
      <NodePalette />
      
      <!-- 中间画布 -->
      <WorkflowCanvas />
      
      <!-- 右侧属性面板 -->
      <PropertyPanel />
    </div>
    
    <!-- 执行结果面板 -->
    <ExecutionPanel v-if="showExecutionPanel" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useWorkflowStore } from '@/stores/workflow'
import EditorToolbar from '@/components/EditorToolbar.vue'
import NodePalette from '@/components/NodePalette.vue'
import WorkflowCanvas from '@/components/WorkflowCanvas.vue'
import PropertyPanel from '@/components/PropertyPanel.vue'
import ExecutionPanel from '@/components/ExecutionPanel.vue'

const route = useRoute()
const workflowStore = useWorkflowStore()

const showExecutionPanel = ref(false)

onMounted(async () => {
  const workflowId = route.params.id as string
  
  if (workflowId) {
    // 加载现有工作流
    try {
      await workflowStore.loadWorkflow(workflowId)
    } catch (e) {
      console.error('Failed to load workflow:', e)
      // 创建新工作流
      workflowStore.createWorkflow()
    }
  } else {
    // 创建新工作流
    workflowStore.createWorkflow()
  }
})
</script>

<style scoped>
.editor {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f8fafc;
}

.editor-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}
</style>
