<template>
  <div class="sidebar">
    <div class="sidebar-header">
      节点组件
    </div>
    <div class="sidebar-content">
      <div class="node-palette">
        <div 
          v-for="(types, category) in nodeTypesByCategory" 
          :key="category"
          class="node-category"
        >
          <div class="category-title">{{ category }}</div>
          <div
            v-for="nodeType in types"
            :key="nodeType.code"
            class="node-item"
            draggable="true"
            @dragstart="handleDragStart($event, nodeType)"
          >
            <div 
              class="node-item-icon" 
              :style="{ background: nodeType.color }"
            >
              {{ nodeType.icon }}
            </div>
            <div class="node-item-info">
              <div class="node-item-name">{{ nodeType.name }}</div>
              <div class="node-item-desc">{{ nodeType.description }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nodeTypesByCategory } from '@/config/nodeTypes'
import type { NodeTypeInfo } from '@/types/workflow'

function handleDragStart(event: DragEvent, nodeType: NodeTypeInfo) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/node-type', nodeType.code)
    event.dataTransfer.effectAllowed = 'copy'
  }
}
</script>

<style scoped>
.sidebar {
  width: 280px;
  background: white;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.node-palette {
  .node-category {
    margin-bottom: 16px;
    
    .category-title {
      font-size: 11px;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      margin-bottom: 8px;
      padding: 0 4px;
      letter-spacing: 0.5px;
    }
  }
  
  .node-item {
    display: flex;
    align-items: center;
    padding: 10px 12px;
    border-radius: 8px;
    cursor: grab;
    transition: all 0.2s;
    margin-bottom: 4px;
    border: 1px solid transparent;
    
    &:hover {
      background: #f8fafc;
      border-color: #e2e8f0;
    }
    
    &:active {
      cursor: grabbing;
    }
    
    .node-item-icon {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 12px;
      font-size: 16px;
      color: white;
      flex-shrink: 0;
    }
    
    .node-item-info {
      flex: 1;
      min-width: 0;
      
      .node-item-name {
        font-size: 13px;
        font-weight: 500;
        color: #1e293b;
      }
      
      .node-item-desc {
        font-size: 11px;
        color: #94a3b8;
        margin-top: 2px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }
}
</style>
