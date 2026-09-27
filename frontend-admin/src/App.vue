<!-- 机构端应用框架：侧边导航、当前页面标题与路由内容区域。 -->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
// 根据当前路由计算页头标题，区分课程新建与已有课程编辑。
const title = computed(() => {
  if (route.path.startsWith('/courses/'))
    return route.path === '/courses/new' ? '创建课程' : '编辑课程'
  return (
    (
      {
        '/': '工作台',
        '/courses': '课程管理',
        '/media': '媒资中心',
        '/review': '审核工作台',
      } as Record<string, string>
    )[route.path] || '教学管理'
  )
})
const nav = [
  { path: '/', icon: '⌂', label: '工作台' },
  { path: '/courses', icon: '▤', label: '课程管理' },
  { path: '/media', icon: '▣', label: '媒资中心' },
  { path: '/review', icon: '✓', label: '审核工作台' },
]
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <RouterLink
        to="/"
        class="brand"
      >
        <span class="brand-mark">✳</span>
        <span>
          <strong>小葵花课堂</strong>
          <small>教学管理中心</small>
        </span>
      </RouterLink>
      <div class="side-caption">工作空间</div>
      <nav class="side-nav">
        <RouterLink
          v-for="item in nav"
          :key="item.path"
          :to="item.path"
          :class="{
            active:
              route.path === item.path ||
              (item.path === '/courses' && route.path.startsWith('/courses/')),
          }"
        >
          <span class="nav-icon">{{ item.icon }}</span>
          {{ item.label }}
          <span class="nav-arrow">›</span>
        </RouterLink>
      </nav>
      <div class="side-bottom">
        <div class="side-help">
          <span>✦</span>
          <div>
            <strong>专注每一步成长</strong>
            <p>把好课程带给更多人</p>
          </div>
        </div>
        <div class="side-account">
          <span class="avatar">教</span>
          <div>
            <strong>教学机构</strong>
            <small>机构工作区</small>
          </div>
          <span>⋯</span>
        </div>
      </div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <div class="breadcrumb">
          教学管理
          <span>/</span>
          <strong>{{ title }}</strong>
        </div>
        <div class="top-actions">
          <span class="top-date">
            {{
              new Date().toLocaleDateString('zh-CN', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
              })
            }}
          </span>
          <span class="top-avatar">教</span>
        </div>
      </header>
      <main class="page"><RouterView /></main>
    </div>
  </div>
</template>
