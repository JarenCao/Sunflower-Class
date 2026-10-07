<!-- 课程详情：展示发布快照中的介绍、价格及目录；收费选课接入真实订单服务。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourse, getEnrollment, enrollCourse, createCourseOrder } from '../data'
import { identity, loadIdentity } from '../auth'
import type { Course, CourseEnrollment } from '../data'
const route = useRoute()
const course = ref<Course>()
const coverFailed = ref(false)
// 详情切换课程时重新尝试加载对应的真实封面。
watch(
  () => course.value?.pic,
  () => {
    coverFailed.value = false
  },
)
const router = useRouter()
const notice = ref('')
const enrollment = ref<CourseEnrollment | null>(null)
const enrolling = ref(false)
const loading = ref(true)
// 根据网址中的课程编号读取发布详情，失败时清空旧课程展示。
async function load() {
  loading.value = true
  notice.value = ''
  enrollment.value = null
  try {
    course.value = await getCourse(Number(route.params.id))
    if (identity.value?.role === 'student') {
      try {
        enrollment.value = await getEnrollment(course.value.id)
      } catch (error) {
        notice.value = errorMessage(error)
      }
    }
  } catch {
    course.value = undefined
  } finally {
    loading.value = false
  }
}
/** 登录后由服务端创建选课；重复选课或待支付不会重复创建记录。 */
async function enroll() {
  if (!course.value || enrolling.value) return
  enrolling.value = true
  notice.value = ''
  try {
    const user = await loadIdentity()
    if (user?.role !== 'student') {
      await router.push({ path: '/login', query: { redirect: route.fullPath } })
      return
    }
    if (enrollment.value?.status === '70201') {
      await router.push('/my-courses')
      return
    }
    enrollment.value = await enrollCourse(course.value.id)
    if (enrollment.value.status === '70202') {
      const order = await createCourseOrder(course.value.id)
      await router.push({ path: '/orders', query: { id: order.id } })
      return
    }
    notice.value =
      enrollment.value.status === '70201'
        ? '选课成功，已加入我的学习。'
        : '已记录待支付选课，请前往订单完成支付。'
  } catch (error) {
    notice.value = errorMessage(error)
  } finally {
    enrolling.value = false
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
          <img
            v-if="course.pic && !coverFailed"
            :src="course.pic"
            :alt="`${course.title}的封面`"
            class="detail-cover-image"
            @error="coverFailed = true"
          />
          <span
            v-else
            class="cover-placeholder"
          >
            暂无封面
          </span>
        </div>
      </div>
    </div>
    <div class="container detail-layout">
      <div class="detail-main">
        <div
          v-if="course.teachers.length"
          class="detail-section"
        >
          <h2>课程讲师</h2>
          <div
            v-for="teacher in course.teachers"
            :key="teacher.id"
            class="course-teacher"
          >
            <img
              v-if="teacher.photograph"
              :src="teacher.photograph"
              :alt="teacher.teacherName"
              referrerpolicy="no-referrer"
            />
            <div>
              <h3>
                {{ teacher.teacherName }}
                <small>{{ teacher.position }}</small>
              </h3>
              <p>{{ teacher.introduction }}</p>
            </div>
          </div>
        </div>
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
              <RouterLink
                v-if="lesson.free"
                :to="`/trial/${course.id}/${lesson.id}`"
              >
                立即试学 →
              </RouterLink>
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
          @click="enroll"
          :disabled="enrolling || loading"
        >
          {{
            enrolling
              ? '正在选课…'
              : enrollment?.status === '70201'
                ? '查看我的学习'
                : enrollment?.status === '70202'
                  ? '前往支付'
                  : course.charge === '30201'
                    ? '免费加入学习'
                    : '立即选课'
          }}
          →
        </button>
        <div
          v-if="notice"
          class="integration-note"
        >
          {{ notice }}
        </div>
        <div
          v-if="enrollment?.status"
          class="integration-note"
        >
          {{
            !enrollment.courseAvailable
              ? '课程已下架，暂不可学习'
              : enrollment.qualification === '70303'
                ? '学习资格已过期'
                : enrollment.status === '70202'
                  ? '待支付 · 尚未开通学习资格'
                  : '已选课 · 已开通学习资格'
          }}
          <RouterLink to="/my-courses">查看我的学习 →</RouterLink>
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

<style scoped>
.course-teacher {
  display: flex;
  gap: 18px;
  margin-top: 20px;
}
.course-teacher img {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
}
.course-teacher p {
  white-space: pre-wrap;
}
</style>
