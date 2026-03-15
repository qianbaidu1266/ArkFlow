<template>
  <div class="sidebar sidebar-right">
    <div class="sidebar-header">
      属性配置
    </div>
    <div class="sidebar-content">
      <div v-if="selectedNode" class="property-panel">
        <div class="property-group">
          <label class="property-label">节点名称</label>
          <input 
            v-model="selectedNode.name"
            type="text"
            class="property-input"
            @change="updateNode"
          />
        </div>
        
        <div class="property-group">
          <label class="property-label">节点类型</label>
          <div class="property-static">{{ nodeTypeName }}</div>
        </div>
        
        <template v-for="config in nodeConfigs" :key="config.name">
          <div class="property-group">
            <label class="property-label">
              {{ config.label }}
              <span v-if="config.required" class="required">*</span>
            </label>
            
            <input
              v-if="config.type === 'string'"
              v-model="nodeConfig[config.name]"
              type="text"
              class="property-input"
              :placeholder="config.description"
              @change="updateConfig"
            />
            
            <input
              v-else-if="config.type === 'number'"
              v-model.number="nodeConfig[config.name]"
              type="number"
              class="property-input"
              :placeholder="config.description"
              @change="updateConfig"
            />
            
            <textarea
              v-else-if="config.type === 'textarea'"
              v-model="nodeConfig[config.name]"
              class="property-input"
              :placeholder="config.description"
              rows="4"
              @change="updateConfig"
            ></textarea>
            
            <select
              v-else-if="config.type === 'select'"
              v-model="nodeConfig[config.name]"
              class="property-input"
              @change="updateConfig"
            >
              <option 
                v-for="option in config.options" 
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </select>
            
            <textarea
              v-else-if="config.type === 'json'"
              v-model="jsonValues[config.name]"
              class="property-input"
              :placeholder="config.description"
              rows="4"
              @change="updateJsonConfig(config.name)"
            ></textarea>
            
            <label v-else-if="config.type === 'boolean'" class="checkbox-label">
              <input
                v-model="nodeConfig[config.name]"
                type="checkbox"
                @change="updateConfig"
              />
              {{ config.description }}
            </label>
            
            <div v-else-if="config.type === 'array'" class="array-editor">
              <div 
                v-for="(item, index) in (nodeConfig[config.name] || [])" 
                :key="index"
                class="array-item"
              >
                <input
                  v-model="nodeConfig[config.name][index]"
                  type="text"
                  class="property-input"
                  @change="updateConfig"
                />
                <button class="btn-remove" @click="removeArrayItem(config.name, index)">×</button>
              </div>
              <button class="btn-add" @click="addArrayItem(config.name)">+ 添加</button>
            </div>
            
            <div v-if="config.description && config.type !== 'boolean'" class="property-hint">
              {{ config.description }}
            </div>
          </div>
        </template>
        
        <div class="property-actions">
          <button class="btn-delete" @click="deleteNode">
            删除节点
          </button>
        </div>
      </div>
      
      <div v-else class="empty-state">
        <div class="empty-icon">◈</div>
        <div class="empty-text">选择一个节点以配置属性</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useWorkflowStore } from '@/stores/workflow'
import { useCanvasStore } from '@/stores/canvas'
import { getNodeTypeInfo, getNodeConfigDefs } from '@/config/nodeTypes'

const workflowStore = useWorkflowStore()
const canvasStore = useCanvasStore()

const jsonValues = ref<Record<string, string>>({})

const selectedNode = computed(() => {
  const nodeId = canvasStore.editingNodeId
  return nodeId ? workflowStore.nodes[nodeId] : null
})

const nodeTypeName = computed(() => {
  if (!selectedNode.value) return ''
  return getNodeTypeInfo(selectedNode.value.type)?.name || selectedNode.value.type
})

const nodeConfigs = computed(() => {
  if (!selectedNode.value) return []
  return getNodeConfigDefs(selectedNode.value.type)
})

const nodeConfig = computed({
  get: () => selectedNode.value?.config || {},
  set: (val) => {
    if (selectedNode.value) {
      workflowStore.updateNodeConfig(selectedNode.value.id, val)
    }
  }
})

watch(selectedNode, (node) => {
  if (node) {
    nodeConfigs.value.forEach(config => {
      if (config.type === 'json' && node.config[config.name]) {
        jsonValues.value[config.name] = JSON.stringify(node.config[config.name], null, 2)
      }
    })
  }
}, { immediate: true })

function updateNode() {
  if (selectedNode.value) {
    workflowStore.updateNode(selectedNode.value.id, { name: selectedNode.value.name })
  }
}

function updateConfig() {
  if (selectedNode.value) {
    workflowStore.updateNodeConfig(selectedNode.value.id, nodeConfig.value)
  }
}

function updateJsonConfig(name: string) {
  try {
    const value = JSON.parse(jsonValues.value[name])
    nodeConfig.value[name] = value
    updateConfig()
  } catch (e) {
  }
}

function addArrayItem(name: string) {
  if (!nodeConfig.value[name]) {
    nodeConfig.value[name] = []
  }
  nodeConfig.value[name].push('')
  updateConfig()
}

function removeArrayItem(name: string, index: number) {
  if (nodeConfig.value[name]) {
    nodeConfig.value[name].splice(index, 1)
    updateConfig()
  }
}

function deleteNode() {
  if (selectedNode.value && confirm('确定要删除这个节点吗？')) {
    workflowStore.deleteNode(selectedNode.value.id)
    canvasStore.clearSelection()
  }
}
</script>

<style scoped>
.sidebar-right {
  width: 320px;
  border-right: none;
  border-left: 1px solid #e2e8f0;
}

.property-panel {
  padding: 16px;
}

.property-group {
  margin-bottom: 20px;
}

.property-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
  
  .required {
    color: #ef4444;
    margin-left: 2px;
  }
}

.property-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 13px;
  transition: all 0.2s;
  
  &:focus {
    outline: none;
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }
}

textarea.property-input {
  min-height: 80px;
  resize: vertical;
  font-family: inherit;
}

select.property-input {
  cursor: pointer;
}

.property-static {
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 13px;
  color: #64748b;
}

.property-hint {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  
  input[type="checkbox"] {
    width: 16px;
    height: 16px;
    cursor: pointer;
  }
}

.array-editor {
  .array-item {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
    
    .property-input {
      flex: 1;
    }
  }
  
  .btn-remove {
    width: 32px;
    height: 32px;
    border: none;
    background: #fee2e2;
    color: #ef4444;
    border-radius: 6px;
    cursor: pointer;
    font-size: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    
    &:hover {
      background: #fecaca;
    }
  }
  
  .btn-add {
    width: 100%;
    padding: 8px;
    border: 1px dashed #d1d5db;
    background: transparent;
    color: #64748b;
    border-radius: 6px;
    cursor: pointer;
    font-size: 13px;
    transition: all 0.2s;
    
    &:hover {
      border-color: #3b82f6;
      color: #3b82f6;
    }
  }
}

.property-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.btn-delete {
  width: 100%;
  padding: 10px;
  border: none;
  background: #fee2e2;
  color: #ef4444;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s;
  
  &:hover {
    background: #fecaca;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: #94a3b8;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  color: #cbd5e1;
}

.empty-text {
  font-size: 14px;
}
</style>
