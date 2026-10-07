<!-- 审核工作台：查看课程、提交审核结论及追踪操作记录。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PAGE_SIZE, getAuditHistory, listAuditCourses, getAuditDetail, reviewCourse } from '../api'
import type { Course, CourseAuditRecord, ReviewSnapshot, Teachplan, CourseTeacher } from '../types'

const rows = ref<Course[]>([])
const page = ref(1)
const count = ref(0)
const status = ref('30403')
const loading = ref(false)
const processingId = ref<number | null>(null)
const historyOpen = ref(false)
const history = ref<CourseAuditRecord[]>([])
const historyCourseName = ref('')
const detailOpen = ref(false)
const detail = ref<ReviewSnapshot | null>(null)
const plans = ref<Teachplan[]>([])
const teachers = ref<CourseTeacher[]>([])

/** 只读提交快照，目录解析失败时给出错误，不能伪造空目录。 */
async function showDetail(course: Course) {
  try {
    const snapshot = await getAuditDetail(course.id)
    const tree = JSON.parse(snapshot.teachplan || '[]')
    if (!Array.isArray(tree)) throw new Error('审核快照目录格式无效')
    // 平台管理员读取提交时冻结的师资快照，不查询可变草稿。
    const staff = JSON.parse(snapshot.teachers || '[]')
    if (!Array.isArray(staff)) throw new Error('师资快照格式无效')
    teachers.value = staff
    detail.value = snapshot
    plans.value = tree
    detailOpen.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

// 切换审核状态时回到第一页。
function changeStatus() {
  page.value = 1
  void refresh()
}

// 读取当前状态的一页课程，并在处理完最后一行后回到前一页。
async function refresh() {
  loading.value = true
  try {
    const result = await listAuditCourses(page.value, PAGE_SIZE, status.value)
    rows.value = result.items
    count.value = result.count
    if (page.value > 1 && rows.value.length === 0) {
      page.value -= 1
      await refresh()
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

// 通过需确认，驳回需填写不超过数据库字段长度的原因。
async function decide(course: Course, approved: boolean) {
  let reason = ''
  try {
    if (approved) {
      await ElMessageBox.confirm(`确定通过“${course.name}”的审核吗？`, '审核通过', {
        confirmButtonText: '确认通过',
        cancelButtonText: '取消',
      })
    } else {
      const answer = await ElMessageBox.prompt(`填写“${course.name}”的驳回原因`, '审核驳回', {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValidator: (value) =>
          value.trim().length > 0 && value.trim().length <= 255
            ? true
            : '请输入 1～255 个字符的驳回原因',
      })
      reason = answer.value.trim()
    }
  } catch {
    return
  }
  processingId.value = course.id
  try {
    await reviewCourse(course.id, approved, reason)
    ElMessage.success(approved ? '审核已通过' : '课程已驳回')
    await refresh()
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    processingId.value = null
  }
}

// 从服务端读取指定课程的历次审核操作记录。
async function showHistory(course: Course) {
  try {
    history.value = await getAuditHistory(course.id)
    historyCourseName.value = course.name
    historyOpen.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error))
  }
}

// 首次进入工作台时加载待审核课程。
onMounted(refresh)
</script>

<template>
  <div class="page-heading compact">
    <div>
      <div class="eyebrow">REVIEW · 审核工作台</div>
      <h1>课程审核</h1>
      <p>查看课程详情，记录审核结论与意见。</p>
    </div>
  </div>
  <div class="review-summary">
    <span class="metric-icon peach">◷</span>
    <div>
      <strong>
        {{ count }} 门课程{{
          status === '30403' ? '待审核' : status === '30404' ? '已通过' : '已驳回'
        }}
      </strong>
      <p>审核记录会保留审核人、时间与驳回原因。</p>
    </div>
  </div>
  <div class="panel">
    <div class="panel-toolbar">
      <div class="toolbar-title">
        <h2>课程审核</h2>
        <span>每页 {{ PAGE_SIZE }} 门课程</span>
      </div>
      <el-radio-group
        v-model="status"
        @change="changeStatus"
      >
        <el-radio-button value="30403">待审核</el-radio-button>
        <el-radio-button value="30404">已通过</el-radio-button>
        <el-radio-button value="30401">已驳回</el-radio-button>
      </el-radio-group>
    </div>
    <el-table
      v-loading="loading"
      :data="rows"
      empty-text="暂无课程"
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
        min-width="330"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="showDetail(row)"
          >
            查看快照
          </el-button>
          <el-button
            link
            type="info"
            @click="showHistory(row)"
          >
            审核记录
          </el-button>
          <template v-if="status === '30403'">
            <el-button
              link
              type="success"
              :loading="processingId === row.id"
              @click="decide(row, true)"
            >
              通过
            </el-button>
            <el-button
              link
              type="danger"
              :loading="processingId === row.id"
              @click="decide(row, false)"
            >
              驳回
            </el-button>
          </template>
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

  <el-dialog
    v-model="detailOpen"
    :title="`${detail?.name || ''} · 提交快照`"
    width="760px"
  >
    <template v-if="detail">
      <p>所属机构：{{ detail.companyName || detail.companyId }}</p>
      <p>{{ detail.description }}</p>
      <p>{{ detail.charge === '30201' ? '免费课程' : `收费课程 ¥${detail.price}` }}</p>
      <h3>提交时的课程师资</h3>
      <p v-if="!teachers.length">暂无讲师介绍</p>
      <div
        v-for="teacher in teachers"
        :key="teacher.id"
      >
        <strong>{{ teacher.teacherName }} · {{ teacher.position }}</strong>
        <p>{{ teacher.introduction }}</p>
      </div>
      <h3>提交时的教学目录</h3>
      <el-table
        :data="plans"
        row-key="id"
        :tree-props="{ children: 'teachPlanTreeNodes' }"
        default-expand-all
      >
        <el-table-column
          prop="pname"
          label="章节与小节"
        />
        <el-table-column
          prop="mediaFilename"
          label="关联媒资"
        />
      </el-table>
    </template>
  </el-dialog>

  <el-dialog
    v-model="historyOpen"
    :title="`${historyCourseName} · 审核记录`"
    width="700px"
  >
    <el-table
      :data="history"
      empty-text="暂无审核记录"
    >
      <el-table-column
        label="结果"
        width="100"
      >
        <template #default="{ row }">{{ row.status === '30404' ? '通过' : '驳回' }}</template>
      </el-table-column>
      <el-table-column
        prop="reason"
        label="审核意见"
        min-width="220"
      />
      <el-table-column
        prop="reviewer"
        label="审核人"
        width="120"
      />
      <el-table-column
        prop="reviewedAt"
        label="审核时间"
        width="180"
      />
    </el-table>
  </el-dialog>
</template>
