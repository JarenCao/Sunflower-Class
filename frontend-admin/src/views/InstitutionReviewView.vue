<!-- 平台管理员统一审核老师申请和课程；真实审核成功后刷新队列。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import axios from 'axios'
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { csrfHeaders } from '../auth'
const client = axios.create({ baseURL: '/api/auth/platform', timeout: 15000 })
const rows = ref<any[]>([]),
  count = ref(0),
  page = ref(1),
  status = ref('40101'),
  loading = ref(false),
  busy = ref(false),
  error = ref('')
const statuses: Record<string, string> = { '40101': '待审核', '40102': '已通过', '40103': '已驳回' }
async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await client.get('/institution-applications', {
      params: { pageNo: page.value, status: status.value || undefined },
    })
    rows.value = data.items
    count.value = data.count
  } catch (failure: any) {
    rows.value = []
    error.value = errorMessage(failure)
  } finally {
    loading.value = false
  }
}
/** 明确审核目标和结果，审核说明必填；后台事务负责整个开户。 */
async function review(row: any, approved: boolean) {
  if (busy.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      `${approved ? '通过并开通' : '驳回'}“${row.adminName}”的老师申请。请填写审核说明。`,
      '老师申请审核',
      {
        inputType: 'textarea',
        inputValidator: (value) =>
          (!!value?.trim() && value.length <= 500) || '请填写不超过500字的说明',
        confirmButtonText: approved ? '通过并开通' : '确认驳回',
        cancelButtonText: '取消',
      },
    )
    busy.value = true
    await client.post(
      `/institution-applications/${row.id}/review`,
      { approved, reason: value },
      { headers: await csrfHeaders() },
    )
    ElMessage.success(approved ? '老师账号已开通' : '申请已驳回')
    await load()
  } catch (failure: any) {
    if (failure !== 'cancel' && failure !== 'close')
      ElMessage.error(errorMessage(failure, '审核失败'))
  } finally {
    busy.value = false
  }
}
async function filterChanged() {
  page.value = 1
  await load()
}
onMounted(load)
</script>
<template>
  <section>
    <h1>老师申请审核</h1>
    <p>核对姓名、联系方式及教学简介，通过后开通独立老师账号与教学空间。</p>
    <select
      v-model="status"
      @change="filterChanged"
    >
      <option value="">全部状态</option>
      <option
        v-for="(label, value) in statuses"
        :key="value"
        :value="value"
      >
        {{ label }}
      </option>
    </select>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <el-table
      v-loading="loading"
      :data="rows"
    >
      <el-table-column
        prop="adminName"
        label="姓名"
      />
      <el-table-column
        prop="mobile"
        label="手机号"
      />
      <el-table-column
        prop="intro"
        label="简介"
      />
      <el-table-column
        prop="adminUsername"
        label="老师账号"
      />
      <el-table-column label="状态">
        <template #default="{ row }">{{ statuses[row.status] }}</template>
      </el-table-column>
      <el-table-column
        prop="reason"
        label="审核说明"
      />
      <el-table-column
        label="操作"
        width="180"
      >
        <template #default="{ row }">
          <template v-if="row.status === '40101'">
            <el-button
              :disabled="busy"
              @click="review(row, true)"
            >
              通过
            </el-button>
            <el-button
              :disabled="busy"
              @click="review(row, false)"
            >
              驳回
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="10"
      :total="count"
      layout="prev,pager,next,total"
      @current-change="load"
    />
  </section>
</template>
