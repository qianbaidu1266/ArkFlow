<template>
  <div
    class="workflow-node"
    :class="[
      `node-${node.type}`,
      { selected }
    ]"
    :style="nodeStyle"
    @mousedown.stop="$emit('mousedown', $event)"
    @click.stop
  >
    <!-- 输入连接点 -->
    <div 
      v-if="node.type !== 'start'"
      class="node-port input"
      @mousedown.stop="$emit('port-mousedown', $event)"
      @mouseup.stop="$emit('port-mouseup', $event)"
    ></div>
    
    <!-- 节点头部 -->
    <div class="node-header">
      <div 
        class="node-icon"
        :style="{ background: nodeTypeInfo?.color || '#94a3b8' }"
      >
        {{ nodeTypeInfo?.icon }}
      </div>
      <div class="node-title">{{ node.name }}</div>
      
      <!-- 更多操作按钮 -->
      <div class="node-menu-wrapper" v-if="selected">
        <button 
          class="menu-trigger"
          title="更多操作"
          @mousedown.stop
          @click.stop="toggleMenu"
        >
          ⋮
        </button>
        
        <!-- 下拉菜单 -->
        <div v-if="showMenu" class="node-menu" v-click-outside="closeMenu">
          <div class="menu-item" @click.stop="handleCopy">
            <span class="menu-icon">⧉</span>
            <span class="menu-text">复制</span>
          </div>
          <div class="menu-item delete" @click.stop="handleDelete">
            <span class="menu-icon">🗑</span>
            <span class="menu-text">删除</span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 节点内容 -->
    <div class="node-content">
      <div v-if="hasConfig" class="node-preview">
        {{ configPreview }}
      </div>
    </div>
    
    <!-- 输出连接点 -->
    <div 
      v-if="node.type !== 'end'"
      class="node-port output"
      @mousedown.stop="$emit('port-mousedown', $event)"
      @mouseup.stop="$emit('port-mouseup', $event)"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WorkflowNode as WorkflowNodeType } from '@/types/workflow'
import { getNodeTypeInfo } from '@/config/nodeTypes'

const props = defineProps<{
  node: WorkflowNodeType
  selected: boolean
}>()

const emit = defineEmits<{
  (e: 'mousedown', event: MouseEvent): void
  (e: 'port-mousedown', event: MouseEvent): void
  (e: 'port-mouseup', event: MouseEvent): void
  (e: 'delete'): void
  (e: 'copy'): void
}>()

const showMenu = ref(false)

const nodeTypeInfo = computed(() => getNodeTypeInfo(props.node.type))

const nodeStyle = computed(() => ({
  left: `${props.node.position.x}px`,
  top: `${props.node.position.y}px`
}))

const hasConfig = computed(() => {
  return Object.keys(props.node.config).length > 0
})

const configPreview = computed(() => {
  const config = props.node.config
  const previews: string[] = []
  
  if (config.model) {
    previews.push(config.model)
  }
  if (config.outputKey && config.outputKey !== 'output') {
    previews.push(`→ ${config.outputKey}`)
  }
  if (config.query || config.userPrompt) {
    const text = config.query || config.userPrompt
    if (text) {
      previews.push(text.slice(0, 30) + (text.length > 30 ? '...' : ''))
    }
  }
  
  return previews.join(' | ') || '已配置'
})

function toggleMenu() {
  showMenu.value = !showMenu.value
}

function closeMenu() {
  showMenu.value = false
}

function handleCopy() {
  emit('copy')
  closeMenu()
}

function handleDelete() {
  emit('delete')
  closeMenu()
}
</script>

<script lang="ts">
// 点击外部指令
export default {
  directives: {
    'click-outside': {
      mounted(el: HTMLElement, binding: any) {
        el._clickOutside = (event: Event) => {
          if (!(el === event.target || el.contains(event.target as Node))) {
            binding.value()
          }
        }
        document.addEventListener('click', el._clickOutside)
      },
      unmounted(el: HTMLElement) {
        document.removeEventListener('click', el._clickOutside)
      }
    }
  }
}
</script>

<style scoped>
.workflow-node {
  position: absolute;
  width: 180px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: move;
  user-select: none;
  transition: box-shadow 0.2s;
  
  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  }
  
  &.selected {
    box-shadow: 0 0 0 2px #3b82f6, 0 4px 16px rgba(0, 0, 0, 0.15);
  }
  
  &.node-start {
    border-top: 3px solid #10b981;
  }
  
  &.node-end {
    border-top: 3px solid #ef4444;
  }
  
  &.node-llm {
    border-top: 3px solid #8b5cf6;
  }
  
  &.node-agent {
    border-top: 3px solid #f59e0b;
  }
  
  &.node-condition {
    border-top: 3px solid #ec4899;
  }
  
  &.node-knowledge_retrieval {
    border-top: 3px solid #06b6d4;
  }
  
  &.node-code {
    border-top: 3px solid #6366f1;
  }
  
  &.node-http {
    border-top: 3px solid #84cc16;
  }
  
  &.node-template {
    border-top: 3px solid #f97316;
  }
  
  &.node-variable_assigner {
    border-top: 3px solid #14b8a6;
  }
}

.node-header {
  display: flex;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f1f5f9;
}

.node-icon {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  font-size: 14px;
  color: white;
  flex-shrink: 0;
}

.node-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-menu-wrapper {
  position: relative;
  margin-left: 8px;
}

.menu-trigger {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  background: transparent;
  color: #64748b;
  
  &:hover {
    background: #f1f5f9;
    color: #1e293b;
  }
}

.node-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  min-width: 120px;
  z-index: 100;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 10px 14px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
  color: #1e293b;
  
  &:hover {
    background: #f8fafc;
  }
  
  &.delete:hover {
    background: #fee2e2;
    color: #ef4444;
  }
}

.menu-icon {
  margin-right: 10px;
  font-size: 14px;
  width: 16px;
  text-align: center;
}

.menu-text {
  flex: 1;
}

.node-content {
  padding: 8px 12px;
  min-height: 32px;
}

.node-preview {
  font-size: 11px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-port {
  position: absolute;
  width: 12px;
  height: 12px;
  background: white;
  border: 2px solid #94a3b8;
  border-radius: 50%;
  cursor: crosshair;
  transition: all 0.2s;
  z-index: 10;
  
  &:hover {
    border-color: #3b82f6;
    background: #3b82f6;
    transform: scale(1.3);
  }
  
  &.input {
    left: -6px;
    top: 50%;
    transform: translateY(-50%);
  }
  
  &.output {
    right: -6px;
    top: 50%;
    transform: translateY(-50%);
  }
}
</style>
