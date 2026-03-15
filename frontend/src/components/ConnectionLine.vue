<template>
  <g 
    class="connection-line"
    :class="{ selected }"
    @click="$emit('click', $event)"
  >
    <path
      :d="pathData"
      fill="none"
      stroke="#94a3b8"
      stroke-width="2"
      :class="{ selected }"
    />
    <!-- 箭头 -->
    <polygon
      :points="arrowPoints"
      fill="#94a3b8"
      :class="{ selected }"
    />
  </g>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowEdge, WorkflowNode } from '@/types/workflow'

const props = defineProps<{
  edge: WorkflowEdge
  fromNode: WorkflowNode
  toNode: WorkflowNode
  selected: boolean
}>()

defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()

const NODE_WIDTH = 180
const NODE_HEIGHT = 80

const pathData = computed(() => {
  const fromX = props.fromNode.position.x + NODE_WIDTH
  const fromY = props.fromNode.position.y + NODE_HEIGHT / 2
  const toX = props.toNode.position.x
  const toY = props.toNode.position.y + NODE_HEIGHT / 2
  
  return generateBezierPath(fromX, fromY, toX, toY)
})

const arrowPoints = computed(() => {
  const toX = props.toNode.position.x
  const toY = props.toNode.position.y + NODE_HEIGHT / 2
  
  return generateArrowPoints(toX, toY)
})

function generateBezierPath(x1: number, y1: number, x2: number, y2: number): string {
  const dx = x2 - x1
  const controlOffset = Math.abs(dx) * 0.5
  
  const c1x = x1 + controlOffset
  const c1y = y1
  const c2x = x2 - controlOffset
  const c2y = y2
  
  return `M ${x1} ${y1} C ${c1x} ${c1y}, ${c2x} ${c2y}, ${x2} ${y2}`
}

function generateArrowPoints(x: number, y: number): string {
  const size = 8
  return `${x},${y} ${x - size},${y - size/2} ${x - size},${y + size/2}`
}
</script>

<style scoped>
.connection-line {
  cursor: pointer;
  
  path {
    transition: stroke 0.2s, stroke-width 0.2s;
    
    &:hover {
      stroke: #3b82f6;
      stroke-width: 3;
    }
    
    &.selected {
      stroke: #3b82f6;
      stroke-width: 3;
    }
  }
  
  polygon {
    transition: fill 0.2s;
    
    &:hover, &.selected {
      fill: #3b82f6;
    }
  }
}
</style>
