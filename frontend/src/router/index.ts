import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/editor/:id?',
    name: 'Editor',
    component: () => import('@/views/Editor.vue')
  },
  {
    path: '/workflows',
    name: 'Workflows',
    component: () => import('@/views/Workflows.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
