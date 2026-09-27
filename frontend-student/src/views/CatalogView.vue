<!-- 课程目录：对已发布课程按分类和关键字筛选，每页展示十条。 -->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CourseCard from '../components/CourseCard.vue'
import { getCourses } from '../data'
import type { Course } from '../data'
const route = useRoute()
const router = useRouter()
const courses = ref<Course[]>([])
const category = ref('全部课程')
const query = ref(String(route.query.q || ''))
// 分类选项从已发布课程中去重生成，避免写死与实际课程无关的分类。
const categories = computed(() => ['全部课程', ...new Set(courses.value.map((c) => c.category))])
const error = ref('')
const pageSize = 10
const page = ref(1)
// 根据筛选后的数量计算页数；空结果时内部页数至少为 1。
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
// 先按分类与关键字筛选，再截取当前页；页码从 1 开始，数组下标从 0 开始。
const visibleCourses = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)
// 筛选条件变化后回到第一页，避免停留在不存在的页码。
watch([query, category], () => {
  page.value = 1
})
// 统一全角英文、大小写和首尾空格，避免 nacos 无法匹配 Nacos。
const normalizedQuery = computed(() => query.value.normalize('NFKC').trim().toLocaleLowerCase())
// 课程必须满足分类条件，同时标题或任意标签包含规范化后的关键字。
const filtered = computed(() =>
  courses.value.filter(
    (c) =>
      (category.value === '全部课程' || c.category === category.value) &&
      [c.title, ...c.tags].some((text) =>
        text.normalize('NFKC').toLocaleLowerCase().includes(normalizedQuery.value),
      ),
  ),
)
// 首次进入目录时从后端分批加载已发布课程，随后在内存中筛选。
onMounted(async () => {
  try {
    courses.value = await getCourses()
  } catch (e) {
    error.value = (e as Error).message
  }
})
// 将搜索词写入网址，刷新页面时可从 q 参数恢复；不使用本地存储。
function search() {
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
        <p>共 {{ filtered.length }} 门课程，等待与你相遇</p>
      </div>
      <span>持续更新中 ✦</span>
    </div>
    <!-- 分类切换：选项来自课程数据，点击后通过计算属性重新筛选。 -->
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
        v-for="course in visibleCourses"
        :key="course.id"
        :course="course"
      />
    </div>
    <nav
      v-if="filtered.length"
      class="catalog-pagination"
      aria-label="课程分页"
    >
      <span>共 {{ filtered.length }} 门 · 每页 10 门</span>
      <button
        :disabled="page === 1"
        @click="page--"
      >
        上一页
      </button>
      <span>{{ page }} / {{ pageCount }}</span>
      <button
        :disabled="page === pageCount"
        @click="page++"
      >
        下一页
      </button>
    </nav>
    <div
      v-if="!filtered.length"
      class="empty-message"
    >
      {{ error || '没有找到已发布课程，试试其他关键词。' }}
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
