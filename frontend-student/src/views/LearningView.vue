<!-- 学习空间：从真实资格接口和发布小节获取短期地址，使用浏览器原生视频播放。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { getCourse, getEnrollment, getPlayback, getTrialPlayback } from '../data'
import type { Course, Lesson } from '../data'
const route = useRoute()
const trial = computed(() => route.path.startsWith('/trial/'))
const course = ref<Course>()
const current = ref<Lesson>()
const videoUrl = ref('')
const loading = ref(false)
const error = ref('')
const playingError = ref('')
// 切换课程或小节时旧异步响应不能覆盖新选择，旧播放地址立即清空。
let version = 0
async function selectLesson(lesson: Lesson) {
  const request = ++version
  current.value = lesson
  videoUrl.value = ''
  error.value = ''
  playingError.value = ''
  loading.value = true
  try {
    if (!course.value || !lesson.id) throw new Error('该小节没有有效的发布编号')
    const playback = await (trial.value
      ? getTrialPlayback(course.value.id, lesson.id)
      : getPlayback(course.value.id, lesson.id))
    if (request === version) videoUrl.value = playback.url
  } catch (failure) {
    if (request === version) error.value = errorMessage(failure)
  } finally {
    if (request === version) loading.value = false
  }
}
/** 页面刷新仍查询服务端资格，未支付、过期或下架都不加载视频。 */
async function load() {
  const request = ++version
  course.value = undefined
  current.value = undefined
  videoUrl.value = ''
  error.value = ''
  playingError.value = ''
  loading.value = true
  try {
    const id = Number(route.params.id)
    // 试学不创建选课，正式学习仍校验本人资格。
    if (!trial.value) {
      const enrollment = await getEnrollment(id)
      if (request !== version) return
      if (enrollment.qualification !== '70301')
        throw new Error(
          enrollment.qualification === '70303'
            ? '学习资格已过期，请返回我的学习。'
            : '尚未获得学习资格、未完成支付或课程已下架，请返回课程详情。',
        )
    }
    const detail = await getCourse(id)
    if (request !== version) return
    course.value = detail
    const lessons = detail.chapters.flatMap((chapter) => chapter.lessons)
    const first = trial.value
      ? lessons.find((lesson) => lesson.id === Number(route.params.lessonId) && lesson.free)
      : lessons[0]
    if (!first)
      throw new Error(trial.value ? '该小节未开放试学，请返回课程详情。' : '课程没有可播放小节')
    await selectLesson(first)
  } catch (failure) {
    if (request === version) error.value = errorMessage(failure)
  } finally {
    if (request === version) loading.value = false
  }
}
watch(() => [route.params.id, route.params.lessonId, trial.value], load, { immediate: true })
onBeforeUnmount(() => {
  version++
  videoUrl.value = ''
})
</script>
<template>
  <div class="learning-page">
    <div class="container">
      <RouterLink
        :to="`/courses/${route.params.id}`"
        class="back-link"
      >
        ← 返回课程详情
      </RouterLink>
      <h1>{{ course?.title || '学习空间' }}</h1>
      <p v-if="trial">课程试学：完整学习请返回课程详情选课。</p>
      <p v-if="loading">正在核对资格并加载视频…</p>
      <p
        v-if="error"
        class="integration-note"
        role="alert"
      >
        {{ error }}
      </p>
      <div
        v-if="course"
        class="learning-grid"
      >
        <div>
          <!-- 使用短期签名地址，不允许把公开目录或旧地址作为播放失败回退。 -->
          <video
            v-if="videoUrl"
            :key="videoUrl"
            :src="videoUrl"
            controls
            playsinline
            preload="metadata"
            style="width: 100%; background: #111; border-radius: 16px"
            @error="playingError = '视频加载失败，地址可能已过期，请重新获取。'"
          />
          <p
            v-if="playingError"
            role="alert"
          >
            {{ playingError }}
          </p>
          <button
            v-if="current && (playingError || error)"
            :disabled="loading"
            @click="selectLesson(current)"
          >
            重新获取视频
          </button>
          <h2>{{ current?.title }}</h2>
          <p class="lesson-desc">好好享受这一段学习时光。</p>
        </div>
        <!-- 每次切换小节重新授权，不记录或伪造学习进度。 -->
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
              :key="lesson.id"
              :class="{ selected: current?.id === lesson.id }"
              :disabled="trial && !lesson.free"
              @click="selectLesson(lesson)"
            >
              ▶ {{ lesson.title }}
              <small v-if="trial">{{ lesson.free ? '试学' : '选课后学习' }}</small>
              <small>{{ lesson.duration }}</small>
            </button>
          </div>
        </aside>
      </div>
      <RouterLink
        v-if="error && !course"
        to="/my-courses"
      >
        查看我的学习 →
      </RouterLink>
    </div>
  </div>
</template>
