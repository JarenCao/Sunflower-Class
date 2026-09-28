<!-- 课程详情：展示发布快照中的介绍、价格及目录；购买入口尚未接入订单服务。 -->
<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getCourse } from '../data'
import type { Course } from '../data'
const route = useRoute()
const course = ref<Course>()
const notice = ref(false)
const loading = ref(true)
// 根据网址中的课程编号读取发布详情，失败时清空旧课程展示。
async function load() {
  loading.value = true
  try {
    course.value = await getCourse(Number(route.params.id))
  } catch {
    course.value = undefined
  } finally {
    loading.value = false
  }
}
// 首次进入页面读取课程；之后同组件内切换课程由下方监听器处理。
onMounted(load)
// 路由复用同一详情组件时，监听编号变化以加载另一门课程。
watch(() => route.params.id, load)
</script>
<template>
  <div v-if="course">
    <div class="detail-hero">
      <div class="container detail-hero-inner">
        <div>
          <div class="detail-breadcrumb">首页 / 全部课程 / {{ course.category }}</div>
          <span class="detail-label">✦ {{ course.category }} · {{ course.level }}</span>
          <h1>{{ course.title }}</h1>
          <p>{{ course.subtitle }}</p>
          <div class="detail-meta">
            <span>◷ {{ course.hours }}</span>
            <span>▤ {{ course.lessons }} 节课程</span>
            <span>☺ {{ course.learners }} 人在学</span>
          </div>
        </div>
        <div
          class="detail-cover course-cover"
          :class="`theme-${course.theme}`"
        >
          <span class="cover-symbol">{{ course.symbol }}</span>
        </div>
      </div>
    </div>
    <div class="container detail-layout">
      <div class="detail-main">
        <div class="detail-section">
          <h2>你将收获什么</h2>
          <p>
            {{
              course.subtitle
            }}课程按照循序渐进的方式组织，从基础概念走向实际应用，让每一步都有清晰的方向。
          </p>
          <div class="benefit-grid">
            <span>✓ 系统化的知识路径</span>
            <span>✓ 可随时回看的课程内容</span>
            <span>✓ 从概念走向实践</span>
            <span>✓ 按自己的节奏学习</span>
          </div>
        </div>
        <div class="detail-section">
          <h2>
            课程目录
            <small>共 {{ course.chapters.length }} 章</small>
          </h2>
          <div
            v-for="(chapter, index) in course.chapters"
            :key="chapter.title"
            class="chapter"
          >
            <strong>
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              {{ chapter.title }}
            </strong>
            <div
              v-for="lesson in chapter.lessons"
              :key="lesson.title"
              class="lesson"
            >
              <span>▶</span>
              {{ lesson.title }}
              <em v-if="lesson.free">试听</em>
              <small>{{ lesson.duration }}</small>
            </div>
          </div>
        </div>
      </div>
      <aside class="enroll-card">
        <span class="detail-price">
          {{ course.price ? `¥${course.price}` : '免费' }}
          <del v-if="course.originalPrice">¥{{ course.originalPrice }}</del>
        </span>
        <p>为自己的下一次进步投资</p>
        <button
          class="gold-button"
          @click="notice = true"
        >
          {{ course.price ? '立即选课' : '免费加入学习' }} →
        </button>
        <div
          v-if="notice"
          class="integration-note"
        >
          选课与支付接口尚未接入，暂时无法选课或付款。
        </div>
        <div class="enroll-features">
          <span>▤ {{ course.lessons }} 节精心编排的课程</span>
          <span>◷ 按自己的节奏学习</span>
          <span>✦ 持续积累新的能力</span>
        </div>
        <div class="teacher-line">
          <span>师</span>
          <div>
            <small>课程讲师</small>
            <strong>{{ course.instructor }}</strong>
          </div>
        </div>
      </aside>
    </div>
  </div>
  <div
    v-else
    class="container empty-message"
  >
    {{
      loading ? '正在加载课程…' : '课程暂不可访问，可能已下架或删除，请返回课程列表查看其他课程。'
    }}
  </div>
</template>
