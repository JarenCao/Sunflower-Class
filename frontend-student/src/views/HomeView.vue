<!-- 学员首页：读取已发布课程用于推荐展示，并提供课程浏览入口。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import CourseCard from '../components/CourseCard.vue'
import { getCourses } from '../data'
import type { Course } from '../data'
const courses = ref<Course[]>([])
const error = ref('')
// 首页首次挂载时读取课程，失败则保存错误提示，避免填充演示数据。
onMounted(async () => {
  try {
    courses.value = await getCourses()
  } catch (e) {
    error.value = (e as Error).message
  }
})
</script>
<template>
  <div class="home-hero">
    <div class="container hero-inner">
      <div class="hero-copy">
        <span class="hero-label">✳ LEARN SOMETHING NEW EVERY DAY</span>
        <h1>
          让好奇心
          <br />
          带你去
          <span>
            更远的地方
            <span class="underline-swoosh"></span>
          </span>
        </h1>
        <p>每一门精心打磨的课程，都是一次新的开始。和小葵花课堂一起，把想学的，变成会做的。</p>
        <div class="hero-actions">
          <RouterLink
            to="/courses"
            class="gold-button"
          >
            探索全部课程
            <span>↗</span>
          </RouterLink>
          <RouterLink
            to="/my-courses"
            class="ghost-button"
          >
            我的学习 →
          </RouterLink>
        </div>
        <div class="hero-trust">
          <div class="avatar-stack">
            <span>周</span>
            <span>林</span>
            <span>陈</span>
            <span>李</span>
          </div>
          <div>
            <strong>一起开始新的旅程</strong>
            <small>在这里，学习有温度</small>
          </div>
        </div>
      </div>
      <div class="hero-visual">
        <div class="orbit orbit-one"></div>
        <div class="orbit orbit-two"></div>
        <div class="hero-sun">✳</div>
        <div class="floating-note note-one">
          ✦
          <span>每天进步一点点</span>
        </div>
        <div class="floating-note note-two">
          <span>▶</span>
          随时随地学习
        </div>
        <div class="spark s1">✦</div>
        <div class="spark s2">✧</div>
        <div class="spark s3">✦</div>
      </div>
    </div>
  </div>
  <section class="container stat-row">
    <div>
      <strong>{{ courses.length }}</strong>
      <span>精选课程</span>
    </div>
    <div>
      <strong>多元</strong>
      <span>学习方向</span>
    </div>
    <div>
      <strong>随时</strong>
      <span>开启学习</span>
    </div>
    <div>
      <strong>持续</strong>
      <span>自我成长</span>
    </div>
  </section>
  <section class="container home-section">
    <div class="section-heading">
      <div>
        <span class="section-kicker">PICK YOUR PATH</span>
        <h2>
          找到你的下一门
          <span>心动课程</span>
        </h2>
        <p>从感兴趣的地方出发，慢慢成为想成为的人。</p>
      </div>
      <RouterLink
        to="/courses"
        class="view-all"
      >
        查看全部课程
        <span>↗</span>
      </RouterLink>
    </div>
    <div class="course-grid">
      <CourseCard
        v-for="course in courses.slice(0, 3)"
        :key="course.id"
        :course="course"
      />
    </div>
    <div
      v-if="!courses.length"
      class="empty-message"
    >
      {{ error || '暂时还没有已发布课程。' }}
    </div>
  </section>
  <section class="path-section">
    <div class="container path-inner">
      <div class="path-art">
        <span class="path-circle">✳</span>
        <span class="path-card card-a">今天，我学会了新东西</span>
        <span class="path-card card-b">每一步都算数 ↗</span>
      </div>
      <div class="path-copy">
        <span class="section-kicker">YOUR JOURNEY STARTS HERE</span>
        <h2>
          按照自己的节奏
          <br />
          走一条
          <span>喜欢的路</span>
        </h2>
        <p>把学习融进日常。不论你从哪里开始，都会在这里找到适合自己的下一步。</p>
        <RouterLink
          to="/courses"
          class="dark-button"
        >
          开启学习之旅 ↗
        </RouterLink>
      </div>
    </div>
  </section>
  <section class="container home-section final-section">
    <div class="section-heading">
      <div>
        <span class="section-kicker">MORE TO EXPLORE</span>
        <h2>
          还有这些，值得
          <span>你发现</span>
        </h2>
      </div>
      <RouterLink
        to="/courses"
        class="view-all"
      >
        浏览所有课程 ↗
      </RouterLink>
    </div>
    <div class="course-grid">
      <CourseCard
        v-for="course in courses.slice(3, 6)"
        :key="course.id"
        :course="course"
      />
    </div>
  </section>
</template>
