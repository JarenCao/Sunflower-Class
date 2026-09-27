<!-- 工作台：汇总课程、审核、发布与媒资数量，并展示最近课程。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { listCourses, listMedia } from '../api'
import type { Course } from '../types'

const router = useRouter()
const courses = ref<Course[]>([])
const mediaCount = ref(0)
const total = ref(0)
const pending = ref(0)
const published = ref(0)
// 首次进入时并行查询各项统计；卡片使用总条数，最近课程只展示前三条。
onMounted(async () => {
  try {
    const [all, audit, online, media] = await Promise.all([
      listCourses(1, 3),
      listCourses(1, 1, '', '30403'),
      listCourses(1, 1, '', '', '30502'),
      listMedia(),
    ])
    courses.value = all.items
    total.value = all.count
    pending.value = audit.count
    published.value = online.count
    mediaCount.value = media.count
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
})
// 将常见审核编码转换为工作台文案，未列出的编码显示待处理。
const auditLabel = (status: string) =>
  (({ '30402': '待提交', '30403': '审核中', '30404': '已通过' }) as Record<string, string>)[
    status
  ] || '待处理'
</script>

<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">OVERVIEW · 教学工作台</div>
      <h1>让每一门好课，稳稳地走向学员。</h1>
      <p>从课程创建到发布，掌握每一个重要进度。</p>
    </div>
    <RouterLink
      to="/courses/new"
      class="primary-link"
    >
      ＋ 创建新课程
    </RouterLink>
  </div>
  <section class="hero-card">
    <div>
      <span class="hero-kicker">新的教学旅程</span>
      <h2>
        把知识整理成
        <br />
        值得反复学习的课程。
      </h2>
      <p>完成课程信息、编排教学计划、上传并绑定媒资，然后提交审核。</p>
      <RouterLink
        to="/courses/new"
        class="hero-button"
      >
        开始创建
        <span>↗</span>
      </RouterLink>
    </div>
    <div class="hero-art">
      <div class="sun-disc"></div>
      <div class="art-book book-one"></div>
      <div class="art-book book-two"></div>
      <div class="art-line line-one"></div>
      <div class="art-line line-two"></div>
    </div>
  </section>
  <!-- 统计卡片：显示真实接口返回的课程、待审核、已发布和媒资总数。 -->
  <div class="metric-grid">
    <div class="metric-card">
      <span class="metric-icon lavender">▤</span>
      <div>
        <span>课程总数</span>
        <strong>{{ total }}</strong>
        <small>已创建的全部课程</small>
      </div>
    </div>
    <div class="metric-card">
      <span class="metric-icon peach">◷</span>
      <div>
        <span>审核中</span>
        <strong>{{ pending }}</strong>
        <small>等待审核结果</small>
      </div>
    </div>
    <div class="metric-card">
      <span class="metric-icon mint">✦</span>
      <div>
        <span>已发布</span>
        <strong>{{ published }}</strong>
        <small>可被学员发现</small>
      </div>
    </div>
    <div class="metric-card">
      <span class="metric-icon sky">▣</span>
      <div>
        <span>媒资文件</span>
        <strong>{{ mediaCount }}</strong>
        <small>图片与视频素材</small>
      </div>
    </div>
  </div>
  <div class="section-title">
    <div>
      <h2>最近的课程</h2>
      <p>继续完善课程信息与发布流程</p>
    </div>
    <RouterLink
      to="/courses"
      class="text-link"
    >
      查看全部 →
    </RouterLink>
  </div>
  <div class="recent-grid">
    <button
      v-for="(course, index) in courses.slice(0, 3)"
      :key="course.id"
      class="recent-course"
      @click="router.push(`/courses/${course.id}`)"
    >
      <div
        class="recent-cover"
        :class="`cover-${index % 3}`"
      >
        <span>{{ ['✳', '◇', '◎'][index % 3] }}</span>
        <small>{{ (course.tags || '').split(',')[0] }}</small>
      </div>
      <div class="recent-content">
        <span
          class="pill"
          :class="
            course.auditStatus === '30404'
              ? 'pill-green'
              : course.auditStatus === '30403'
                ? 'pill-blue'
                : ''
          "
        >
          {{ auditLabel(course.auditStatus) }}
        </span>
        <h3>{{ course.name }}</h3>
        <p>{{ course.description }}</p>
        <div class="recent-footer">
          <span>{{ course.createDate || '近期创建' }}</span>
          <span>查看课程 ↗</span>
        </div>
      </div>
    </button>
    <div
      v-if="!courses.length"
      class="empty-card"
    >
      暂无课程，点击右上角创建第一门课程。
    </div>
  </div>
</template>
