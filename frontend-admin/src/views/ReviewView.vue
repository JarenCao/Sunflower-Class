<!-- 审核工作台：展示审核中的课程；通过和驳回操作尚未接入。 -->
<script setup lang="ts">
import { PAGE_SIZE } from '../api'
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listCourses } from '../api'
import type { Course } from '../types'
const pending = ref<Course[]>([])
const page = ref(1)
const count = ref(0)
// 30403 表示审核中；当前仅查询待审核列表，尚未接入通过和驳回操作。
async function refresh() {
  try {
    const result = await listCourses(page.value, PAGE_SIZE, '', '30403')
    pending.value = result.items
    count.value = result.count
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}
// 进入审核工作台时加载当前页待审核课程。
onMounted(refresh)
</script>

<template>
  <div class="page-heading compact">
    <div>
      <div class="eyebrow">REVIEW · 审核工作台</div>
      <h1>课程审核</h1>
      <p>认真审核每一份课程内容，让学习体验更值得信赖。</p>
    </div>
  </div>
  <div class="review-summary">
    <span class="metric-icon peach">◷</span>
    <div>
      <strong>{{ count }} 门课程待审核</strong>
      <p>审核通过与驳回接口尚未在后端实现，当前页面仅展示待审核数据。</p>
    </div>
  </div>
  <div class="panel">
    <div class="panel-toolbar">
      <div class="toolbar-title">
        <h2>待审核课程</h2>
        <span>审核操作待后端接入</span>
      </div>
    </div>
    <!-- 待审核列表只提供查看入口，尚未实现审核通过或驳回。 -->
    <el-table
      :data="pending"
      empty-text="暂无待审核课程"
    >
      <el-table-column
        prop="name"
        label="课程名称"
        min-width="280"
      />
      <el-table-column
        prop="tags"
        label="标签"
        min-width="180"
      />
      <el-table-column
        label="收费"
        width="110"
      >
        <template #default="{ row }">
          {{ row.charge === '30201' ? '免费' : row.charge === '30202' ? '收费' : '未设置' }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="140"
      >
        <template #default="{ row }">
          <RouterLink
            :to="`/courses/${row.id}`"
            class="table-link"
          >
            查看详情
          </RouterLink>
        </template>
      </el-table-column>
    </el-table>
    <div style="padding: 18px 24px; display: flex; justify-content: flex-end">
      <el-pagination
        v-model:current-page="page"
        :page-size="PAGE_SIZE"
        :total="count"
        layout="total, prev, pager, next"
        @current-change="refresh"
      />
    </div>
  </div>
</template>
