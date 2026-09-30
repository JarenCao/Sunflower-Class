<!-- 课程管理：分页查询课程，提供编辑、提审、发布、下架及二次确认删除。 -->
<script setup lang="ts">
import { PAGE_SIZE } from '../api'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteCourse,
  listCourses,
  offlineCourse,
  submitAudit,
  publishCourse,
  listPublicationMessages,
  retryPublicationMessage,
} from '../api'
import type { PublicationMessage } from '../api'
import type { Course } from '../types'

const rows = ref<Course[]>([])
const page = ref(1)
const count = ref(0)
const search = ref('')
const loading = ref(false)
const actionId = ref<number | null>(null)
const syncCourseId = ref(0)
const syncOpen = ref(false)
const syncLoading = ref(false)
const syncMessages = ref<PublicationMessage[]>([])
/** 打开同步进度时清空旧课程，所有状态直接读取内容服务。 */
async function showSync(id: number) {
  syncCourseId.value = id
  syncMessages.value = []
  syncOpen.value = true
  await refreshSync()
}
/** 手动刷新同步状态，避免关闭页面后仍存在轮询请求。 */
async function refreshSync() {
  const courseId = syncCourseId.value
  syncLoading.value = true
  try {
    const messages = await listPublicationMessages(courseId)
    if (courseId === syncCourseId.value) syncMessages.value = messages
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    if (courseId === syncCourseId.value) syncLoading.value = false
  }
}
/** 请求恢复后刷新状态；重试提交成功不代表下游业务已经完成。 */
async function retrySync(id: number) {
  syncLoading.value = true
  try {
    await retryPublicationMessage(id)
    ElMessage.success('已安排重试，请稍后刷新同步状态')
    await refreshSync()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    syncLoading.value = false
  }
}
/** 五位课程码与消息处理码分别解释，不混用审核状态。 */
function syncLabel(state: string) {
  return (
    ({ '0': '待处理', '1': '成功', '2': '失败待重试' } as Record<string, string>)[state] || '未知'
  )
}
// 搜索条件变化后从第一页重新查询。
function searchFromFirstPage() {
  page.value = 1
  return refresh()
}

// 读取当前筛选条件对应的一页数据，使用服务端总条数驱动分页器。
async function refresh() {
  loading.value = true
  try {
    const result = await listCourses(page.value, PAGE_SIZE, search.value)
    rows.value = result.items
    count.value = result.count
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}
// 提交审核后重新读取课程列表，避免页面继续显示旧审核状态。
async function audit(id: number) {
  try {
    await submitAudit(id)
    ElMessage.success('已提交审核')
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}
// 发布成功后刷新列表，从服务端获取最新发布状态。
async function publish(id: number) {
  try {
    await publishCourse(id)
    ElMessage.success('课程已发布，搜索与学习目录正在同步')
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}
// 二次确认下架，成功后重新读取服务端发布状态。
async function offline(id: number) {
  try {
    await ElMessageBox.confirm('下架同步完成后学员端将不再显示这门课程。确定下架吗？', '下架课程', {
      type: 'warning',
      confirmButtonText: '确定下架',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  actionId.value = id
  try {
    await offlineCourse(id)
    ElMessage.success('课程已下架')
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    actionId.value = null
  }
}
// 二次确认删除；如果当前页删空，则回到上一页再重新查询。
async function remove(id: number) {
  try {
    await ElMessageBox.confirm(
      '删除后课程与教学计划无法恢复，媒资文件会保留。确定删除吗？',
      '删除课程',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  actionId.value = id
  try {
    await deleteCourse(id)
    ElMessage.success('课程已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
    await refresh()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    actionId.value = null
  }
}
// 首次挂载即读取第一页；后续翻页与搜索复用 refresh。
onMounted(refresh)
// 审核字典码转换为中文标签，未知编码采用兜底文案。
const auditLabel = (s: string) =>
  (
    ({ '30401': '审核驳回', '30402': '未提交', '30403': '审核中', '30404': '审核通过' }) as Record<
      string,
      string
    >
  )[s] || '待处理'
</script>

<template>
  <div class="page-heading compact course-heading">
    <div>
      <div class="eyebrow">COURSES · 课程管理</div>
      <h1>课程管理</h1>
      <p>整理课程信息，把每一步准备工作安排妥当。</p>
    </div>
    <RouterLink
      to="/courses/new"
      class="primary-link"
    >
      ＋ 新建课程
    </RouterLink>
  </div>
  <div class="panel courses-panel">
    <div class="panel-toolbar">
      <div class="toolbar-title">
        <h2>全部课程</h2>
        <span>{{ count }} 门课程</span>
      </div>
      <form
        class="toolbar-actions course-search"
        @submit.prevent="searchFromFirstPage"
      >
        <el-input
          v-model="search"
          aria-label="搜索课程名称"
          placeholder="搜索课程名称"
          clearable
          @clear="searchFromFirstPage"
        />
        <el-button native-type="submit">查询</el-button>
      </form>
    </div>
    <!-- 课程表格：封面加载失败显示占位，收费及审核发布字段转换为中文。 -->
    <el-table
      v-loading="loading"
      :data="rows"
      class="data-table course-table"
      empty-text="还没有课程，先创建一门吧"
    >
      <el-table-column
        label="课程"
        min-width="280"
      >
        <template #default="{ row }">
          <div class="table-course">
            <el-image
              v-if="row.pic"
              :key="`${row.id}:${row.pic}`"
              class="course-thumbnail"
              :src="row.pic"
              fit="cover"
              :alt="`${row.name}的封面`"
            >
              <template #error>
                <span
                  class="cover-fallback"
                  title="封面加载失败，请在课程编辑页重新上传"
                >
                  暂无封面
                </span>
              </template>
            </el-image>
            <span
              v-else
              class="course-thumbnail cover-fallback"
              title="尚未设置课程封面"
            >
              暂无封面
            </span>
            <div class="course-summary">
              <RouterLink
                :to="`/courses/${row.id}`"
                class="course-name"
                :title="row.name"
              >
                {{ row.name }}
              </RouterLink>
              <small :title="row.tags || ''">{{ row.tags || '尚未设置标签' }}</small>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="收费类型"
        width="112"
      >
        <template #default="{ row }">
          <span :class="{ 'muted-value': !row.charge }">
            {{ row.charge === '30201' ? '免费' : row.charge === '30202' ? '收费' : '未设置' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="审核状态"
        width="118"
      >
        <template #default="{ row }">
          <span
            class="pill"
            :class="
              row.auditStatus === '30404'
                ? 'pill-green'
                : row.auditStatus === '30403'
                  ? 'pill-blue'
                  : row.auditStatus === '30401'
                    ? 'pill-red'
                    : ''
            "
          >
            {{ auditLabel(row.auditStatus) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="发布状态"
        width="108"
      >
        <template #default="{ row }">
          <span
            class="publish-state"
            :class="{ 'is-published': row.status === '30502' }"
          >
            <i aria-hidden="true"></i>
            {{ row.status === '30502' ? '已发布' : row.status === '30503' ? '已下线' : '未发布' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="320"
      >
        <template #default="{ row }">
          <!-- 按审核与发布状态展示操作入口，执行时仍由后端判断是否允许。 -->
          <div class="course-actions">
            <RouterLink
              :to="`/courses/${row.id}`"
              class="edit-action"
            >
              编辑与编排
            </RouterLink>
            <el-button
              size="small"
              plain
              @click="showSync(row.id)"
            >
              同步状态
            </el-button>
            <el-button
              v-if="
                ['30401', '30402'].includes(row.auditStatus) &&
                ['30501', '30503'].includes(row.status)
              "
              plain
              type="primary"
              size="small"
              @click="audit(row.id)"
            >
              提交审核
            </el-button>
            <el-button
              v-if="row.auditStatus === '30404' && ['30501', '30503'].includes(row.status)"
              plain
              type="primary"
              size="small"
              @click="publish(row.id)"
            >
              发布课程
            </el-button>
            <el-button
              v-if="row.status === '30502'"
              plain
              type="warning"
              size="small"
              :disabled="actionId !== null"
              @click="offline(row.id)"
            >
              下架
            </el-button>
            <el-button
              v-else-if="['30501', '30503'].includes(row.status)"
              plain
              type="danger"
              size="small"
              :disabled="actionId !== null"
              @click="remove(row.id)"
            >
              删除
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <!-- 服务端分页：固定每页十条，切换页码后重新查询。 -->
    <div class="course-pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="PAGE_SIZE"
        :total="count"
        layout="total, prev, pager, next"
        @current-change="refresh"
      />
    </div>
  </div>
  <div class="notice-strip">
    <span>✦</span>
    <div>
      <strong>课程删除说明</strong>
      已发布课程须先下架。删除会清理课程与教学计划，媒资文件仍保留在媒资中心。
    </div>
  </div>
  <!-- 最近十条消息由后端限制；恢复入口不会改动审核或发布状态。 -->
  <el-dialog
    v-model="syncOpen"
    title="课程同步状态"
    width="900px"
  >
    <el-table
      v-loading="syncLoading"
      :data="syncMessages"
      empty-text="暂无发布或下架事件"
    >
      <el-table-column
        prop="id"
        label="事件"
        width="70"
      />
      <el-table-column
        label="操作"
        width="80"
      >
        <template #default="{ row }">{{ row.businessKey3 === '30503' ? '下架' : '发布' }}</template>
      </el-table-column>
      <el-table-column label="总状态">
        <template #default="{ row }">{{ syncLabel(row.state) }}</template>
      </el-table-column>
      <el-table-column label="投递">
        <template #default="{ row }">{{ syncLabel(row.stageState1) }}</template>
      </el-table-column>
      <el-table-column label="搜索">
        <template #default="{ row }">{{ syncLabel(row.stageState3) }}</template>
      </el-table-column>
      <el-table-column label="学习目录">
        <template #default="{ row }">{{ syncLabel(row.stageState4) }}</template>
      </el-table-column>
      <el-table-column
        prop="executeNum"
        label="投递次数"
        width="90"
      />
      <el-table-column
        label="恢复"
        width="90"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.state !== '1'"
            :disabled="syncLoading"
            size="small"
            @click="retrySync(row.id)"
          >
            重试
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <p>队列投递确认后，收到搜索与学习服务的成功回执才算同步完成。自动投递最多十次。</p>
    <template #footer>
      <el-button
        :disabled="syncLoading"
        @click="refreshSync"
      >
        刷新状态
      </el-button>
      <el-button @click="syncOpen = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.course-heading {
  margin-bottom: 24px;
}
.course-heading .primary-link {
  flex-shrink: 0;
}
.courses-panel .panel-toolbar {
  padding: 20px 24px;
  gap: 16px;
}
.course-search {
  width: 320px;
  max-width: 100%;
}
.course-search .el-input {
  flex: 1;
  min-width: 0;
}
.course-search .el-button {
  flex-shrink: 0;
}
.course-table :deep(.el-table__cell) {
  padding: 16px 0;
}
.course-table :deep(th.el-table__cell) {
  padding: 13px 0;
}
.course-table :deep(.cell) {
  padding: 0 16px;
}
.course-table :deep(.el-table__cell:first-child .cell) {
  padding-left: 24px;
}
.table-course {
  gap: 12px;
  padding: 0;
  min-width: 0;
}
.course-thumbnail {
  flex-shrink: 0;
  width: 72px;
  height: 46px;
  border-radius: 6px;
  overflow: hidden;
  background: #edf2f8;
}
.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #8998ac;
  font-size: 10px;
}
span.course-thumbnail {
  height: 46px;
}
.course-summary {
  min-width: 0;
}
.course-name {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: #2d3d56;
}
.course-name:hover {
  color: #416b9f;
}
.course-summary small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 5px;
  font-size: 11px;
}
.muted-value {
  color: #96a0ae;
  font-size: 11px;
}
.pill {
  padding: 4px 10px;
  font-size: 11px;
  line-height: 20px;
}
.pill-red {
  color: #b75b5b;
  background: #fceeee;
}
.publish-state {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  white-space: nowrap;
  font-size: 12px;
  color: #8590a0;
}
.publish-state i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #bac3cf;
}
.publish-state.is-published {
  color: #438865;
}
.publish-state.is-published i {
  background: #65a781;
}
.course-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}
.edit-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid #dce3ed;
  border-radius: 6px;
  color: #445d80;
  background: #fff;
  font-size: 12px;
  font-weight: 500;
}
.edit-action:hover {
  background: #f2f6fb;
  border-color: #a8bcd8;
}
.edit-action:focus-visible,
.course-name:focus-visible {
  outline: 2px solid #698ebf;
  outline-offset: 3px;
}
.course-actions .el-button {
  margin: 0;
  height: 32px;
  padding: 0 11px;
  border-radius: 6px;
  font-size: 12px;
}
.course-pagination {
  padding: 18px 24px;
  display: flex;
  justify-content: flex-end;
  overflow-x: auto;
}
</style>
