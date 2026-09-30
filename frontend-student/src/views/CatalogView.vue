<!-- 课程目录：通过搜索服务查询已发布课程，服务端每页返回十条。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CourseCard from '../components/CourseCard.vue'
import { searchCourses, getCourseCategories } from '../data'
import type { Course } from '../data'
const route = useRoute()
const router = useRouter()
const courses = ref<Course[]>([])
const category = ref('全部课程')
const query = ref(String(route.query.q || ''))
// 分类由后端单独查询，不能只使用当前十条数据。
const categories = ref<string[]>(['全部课程'])
const error = ref('')
const loading = ref(false)
const total = ref(0)
let requestVersion = 0
const pageSize = 10
const page = ref(1)
// 根据筛选后的数量计算页数；空结果时内部页数至少为 1。
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
// 切换分类后回到第一页，由搜索服务筛选。
watch(category, () => {
  if (page.value === 1) load()
  page.value = 1
})
watch(page, load)
/** 请求版本保护较新筛选结果，避免慢请求覆盖刚切换的分类或页码。 */
async function load() {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const result = await searchCourses(page.value, String(route.query.q || ''), category.value)
    if (version !== requestVersion) return
    courses.value = result.items
    total.value = result.count
  } catch (e) {
    if (version !== requestVersion) return
    courses.value = []
    total.value = 0
    error.value = (e as Error).message
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
// 首次进入目录查询分类和当前页；没有本地存储或演示回退。
onMounted(async () => {
  try {
    categories.value = ['全部课程', ...(await getCourseCategories())]
  } catch (e) {
    error.value = (e as Error).message
  }
  await load()
})
watch(
  () => route.query.q,
  () => {
    query.value = String(route.query.q || '')
    if (page.value === 1) load()
    page.value = 1
  },
)
// 将搜索词写入网址，刷新页面时可从 q 参数恢复；不使用本地存储。
function search() {
  if (String(route.query.q || '') === query.value) {
    if (page.value === 1) load()
    page.value = 1
  }
  router.replace({ path: '/courses', query: query.value ? { q: query.value } : {} })
}
</script>
<template>
  <div class="catalog-banner">
    <div class="container">
      <span class="section-kicker">EXPLORE COURSES</span>
      <h1>想学的，都在这里。</h1>
      <p>找到你的兴趣方向，让每一次点击都离目标更近一步。</p>
      <div class="search-box">
        <span>⌕</span>
        <input
          v-model="query"
          placeholder="搜索课程、技术或感兴趣的话题"
          @keyup.enter="search"
        />
        <button @click="search">搜索课程 →</button>
      </div>
    </div>
  </div>
  <section class="container catalog-body">
    <div class="catalog-heading">
      <div>
        <h2>发现好课程</h2>
        <p>共 {{ total }} 门课程，等待与你相遇</p>
      </div>
      <span>持续更新中 ✦</span>
    </div>
    <!-- 分类切换：选项来自后端，点击后重新查询第一页。 -->
    <div class="category-tabs">
      <button
        v-for="item in categories"
        :key="item"
        :class="{ active: category === item }"
        @click="category = item"
      >
        {{ item }}
      </button>
    </div>
    <!-- 只渲染筛选结果的当前页课程卡片。 -->
    <div class="course-grid">
      <CourseCard
        v-for="course in courses"
        :key="course.id"
        :course="course"
      />
    </div>
    <nav
      v-if="total"
      class="catalog-pagination"
      aria-label="课程分页"
    >
      <span>共 {{ total }} 门 · 每页 10 门</span>
      <button
        :disabled="loading || page === 1"
        @click="page--"
      >
        上一页
      </button>
      <span>{{ page }} / {{ pageCount }}</span>
      <button
        :disabled="loading || page === pageCount"
        @click="page++"
      >
        下一页
      </button>
    </nav>
    <div
      v-if="!courses.length"
      class="empty-message"
    >
      {{ loading ? '正在查询课程…' : error || '没有找到已发布课程，试试其他关键词。' }}
    </div>
  </section>
</template>

<style scoped>
.catalog-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
  margin-top: 28px;
  font-size: 13px;
  color: #6d7671;
}
.catalog-pagination button {
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #e4dfd3;
  border-radius: 6px;
  color: #26373b;
}
.catalog-pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
