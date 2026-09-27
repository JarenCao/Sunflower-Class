<!-- 学习空间：展示课程目录与当前小节；播放地址和学习资格验证尚未接入。 -->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getCourse } from '../data'
import type { Course, Lesson } from '../data'
const route = useRoute()
const course = ref<Course>()
const current = ref<Lesson>()
// 读取当前课程并默认选中首章首节；此处仅选择展示项，不获取播放地址。
onMounted(async () => {
  try {
    course.value = await getCourse(Number(route.params.id))
    current.value = course.value?.chapters[0]?.lessons[0]
  } catch {
    course.value = undefined
  }
})
</script>
<template>
  <div
    class="learning-page"
    v-if="course"
  >
    <div class="container">
      <RouterLink
        :to="`/courses/${course.id}`"
        class="back-link"
      >
        ← 返回课程详情
      </RouterLink>
      <h1>{{ course.title }}</h1>
      <p>学习空间 · 待开放</p>
      <div class="learning-grid">
        <div>
          <!-- 播放功能尚未接入；此区域明确提示需由后端验证学习资格。 -->
          <div class="video-placeholder">
            <div>
              <span>▶</span>
              <strong>视频播放接口待接入</strong>
              <small>获取视频地址前，后端必须验证课程状态和学习资格。</small>
            </div>
          </div>
          <h2>{{ current?.title }}</h2>
          <p class="lesson-desc">好好享受这一段学习时光。</p>
        </div>
        <!-- 目录选择仅改变页面当前小节，不记录学习进度。 -->
        <aside class="lesson-sidebar">
          <h3>课程目录</h3>
          <div
            v-for="chapter in course.chapters"
            :key="chapter.title"
            class="sidebar-chapter"
          >
            <strong>{{ chapter.title }}</strong>
            <button
              v-for="lesson in chapter.lessons"
              :key="lesson.title"
              :class="{ selected: current?.title === lesson.title }"
              @click="current = lesson"
            >
              ▶ {{ lesson.title }}
              <small>{{ lesson.duration }}</small>
            </button>
          </div>
        </aside>
      </div>
    </div>
  </div>
  <div
    v-else
    class="container empty-message"
  >
    课程不存在。
  </div>
</template>
