<!-- 课程卡片：展示封面、分类、价格等信息，点击进入详情，图片失败时显示替代内容。 -->
<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Course } from '../data'
const props = defineProps<{ course: Course }>()
const coverFailed = ref(false)
// 卡片复用且封面地址改变时重置失败状态，让新图片重新尝试加载。
watch(
  // 只监听输入课程的封面字段变化。
  () => props.course.pic,
  // 地址变化后恢复图片渲染分支。
  () => {
    coverFailed.value = false
  },
)
</script>
<template>
  <RouterLink
    :to="`/courses/${course.id}`"
    class="course-card"
  >
    <div
      class="course-cover"
      :class="`theme-${course.theme}`"
    >
      <!-- 有封面地址且未加载失败时显示图片，否则使用下方替代内容。 -->
      <img
        v-if="course.pic && !coverFailed"
        :src="course.pic"
        @error="coverFailed = true"
        alt="课程封面"
        style="position: absolute; width: 100%; height: 100%; object-fit: cover"
      />
      <div
        v-else
        class="cover-glow"
      ></div>
      <span class="cover-symbol">{{ course.symbol }}</span>
      <span class="cover-category">{{ course.category }}</span>
    </div>
    <div class="course-info">
      <div class="course-level">
        {{ course.level }}
        <span>·</span>
        {{ course.lessons }} 节课
      </div>
      <h3>{{ course.title }}</h3>
      <p>{{ course.subtitle }}</p>
      <div class="card-bottom">
        <span class="course-price">
          {{ course.price ? `¥${course.price}` : '免费' }}
          <del v-if="course.originalPrice">¥{{ course.originalPrice }}</del>
        </span>
        <span
          v-if="course.learners !== '—'"
          class="course-learners"
        >
          {{ course.learners }} 人在学
        </span>
      </div>
    </div>
  </RouterLink>
</template>
