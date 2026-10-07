<!-- 我的学习读取当前学生选课记录，保留待支付、过期和下架状态，不生成本地记录。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { onMounted, ref } from 'vue'
import { getMyCourses, renewCourse } from '../data'
import type { CourseEnrollment } from '../data'
const rows = ref<CourseEnrollment[]>([])
const page = ref(1)
const count = ref(0)
const loading = ref(false)
const error = ref('')
const renewing = ref<number | null>(null)
const notice = ref('')
// 图片失败时显示明确占位，不影响课程资格和操作入口。
const failedCovers = ref<Set<number>>(new Set())
// 续期后重新读取真实资格，不以按钮点击作为成功依据。
async function renew(row: CourseEnrollment) {
  if (renewing.value !== null) return
  renewing.value = row.courseId
  notice.value = ''
  try {
    await renewCourse(row.courseId)
    await load()
    notice.value = '免费课程已续期，可以继续学习。'
  } catch (failure) {
    notice.value = errorMessage(failure)
  } finally {
    renewing.value = null
  }
}
/** 分页由服务端按当前学员隔离，刷新后仍读取数据库记录。 */
async function load() {
  loading.value = true
  error.value = ''
  rows.value = []
  try {
    const data = await getMyCourses(page.value)
    failedCovers.value = new Set()
    rows.value = data.items
    count.value = data.count
  } catch (failure) {
    count.value = 0
    error.value = errorMessage(failure)
  } finally {
    loading.value = false
  }
}
function changePage(next: number) {
  page.value = next
  void load()
}
function state(row: CourseEnrollment) {
  if (!row.courseAvailable) return '课程已下架'
  if (row.qualification === '70303') return '学习资格已过期'
  return row.status === '70202' ? '待支付 · 未开通学习资格' : '已选课 · 可学习'
}
onMounted(load)
</script>
<template>
  <div class="inner-banner">
    <div class="container">
      <span class="section-kicker">MY LEARNING</span>
      <h1>我的学习</h1>
      <p>每一步努力，都在这里留下痕迹。</p>
    </div>
  </div>
  <div class="container inner-content">
    <div
      v-if="loading"
      class="empty-message"
    >
      正在读取我的课程…
    </div>
    <div
      v-else-if="error"
      class="empty-message"
      role="alert"
    >
      {{ error }}
      <button @click="load">重试</button>
    </div>
    <div
      v-else-if="!rows.length"
      class="empty-message"
    >
      还没有选课记录。
      <RouterLink to="/courses">浏览已发布课程 →</RouterLink>
    </div>
    <p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <template v-if="!loading && !error && rows.length">
      <p>共 {{ count }} 门已选课程</p>
      <div class="course-grid learning-cards">
        <article
          v-for="row in rows"
          :key="row.courseId"
          class="course-card learning-card"
        >
          <div class="course-cover theme-blue">
            <img
              v-if="row.pic && !failedCovers.has(row.courseId)"
              :src="row.pic"
              :alt="`${row.name}的封面`"
              loading="lazy"
              @error="failedCovers.add(row.courseId)"
            />
            <span
              v-else
              class="cover-placeholder"
            >
              暂无封面
            </span>
            <span
              class="learning-state"
              :class="{ available: row.qualification === '70301' }"
            >
              {{ state(row) }}
            </span>
          </div>
          <div class="course-info">
            <h2>{{ row.name }}</h2>
            <p>选课时间：{{ row.createdAt?.replace('T', ' ') }}</p>
            <p v-if="row.expiresAt">有效期至：{{ row.expiresAt.replace('T', ' ') }}</p>
            <p v-if="row.status === '70202'">待支付金额 ¥{{ row.price }}，请在订单中完成支付。</p>
            <div class="learning-actions">
              <button
                v-if="row.renewable"
                class="gold-button"
                :disabled="renewing !== null"
                @click="renew(row)"
              >
                {{ renewing === row.courseId ? '正在续期…' : '免费续期' }}
              </button>
              <!-- 收费待支付选课使用原有快照创建或复用订单，不能在页面直接开通资格。 -->
              <RouterLink
                v-if="row.courseAvailable && row.status === '70202'"
                :to="{ path: '/orders', query: { course: row.courseId } }"
              >
                前往支付 →
              </RouterLink>
              <RouterLink
                v-if="row.courseAvailable"
                :to="`/courses/${row.courseId}`"
              >
                查看课程详情 →
              </RouterLink>
              <RouterLink
                v-if="row.qualification === '70301'"
                :to="`/learn/${row.courseId}`"
              >
                查看学习目录 →
              </RouterLink>
            </div>
          </div>
        </article>
      </div>
      <div class="pagination">
        <button
          :disabled="page <= 1"
          @click="changePage(page - 1)"
        >
          上一页
        </button>
        <span>第 {{ page }} 页</span>
        <button
          :disabled="page * 10 >= count"
          @click="changePage(page + 1)"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* 复用课程网格和卡片基础样式，窄屏自动调整列数。 */
.learning-cards {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
}
.learning-card {
  height: auto;
  min-width: 0;
}
.learning-card:hover {
  transform: none;
}
.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.learning-state {
  position: absolute;
  left: 14px;
  bottom: 14px;
  padding: 6px 10px;
  border-radius: 6px;
  background: #fff5dc;
  color: #806029;
  font-size: 12px;
}
.learning-state.available {
  background: #e8f5ec;
  color: #276440;
}
.course-info h2 {
  margin: 0 0 14px;
  font-size: 19px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.course-info p {
  flex: none;
  overflow: visible;
  margin-bottom: 8px;
  overflow-wrap: anywhere;
}
.learning-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 18px;
  font-size: 13px;
}
.learning-actions a {
  color: #916715;
}
</style>
