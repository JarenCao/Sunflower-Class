<!-- 平台管理员统一创建和查看老师账号；每个新老师拥有独立教学空间。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createTeacher, getTeachers } from '../auth'
const rows = ref<any[]>([])
const count = ref(0)
const page = ref(1)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const form = reactive({ username: '', name: '', password: '', confirmPassword: '' })
/** 每次从服务端读取当前页，网络失败不显示其他机构或旧分页数据。 */
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getTeachers(page.value)
    rows.value = data.items
    count.value = data.count
  } catch (failure: any) {
    rows.value = []
    error.value = errorMessage(failure)
  } finally {
    loading.value = false
  }
}
/** 创建成功后清空密码并回到第一页，重复账号由数据库约束拒绝。 */
async function submit() {
  if (busy.value) return
  busy.value = true
  try {
    await createTeacher(form)
    Object.assign(form, { username: '', name: '', password: '', confirmPassword: '' })
    ElMessage.success('老师账号已创建')
    page.value = 1
    await load()
  } catch (failure: any) {
    ElMessage.error(errorMessage(failure, '创建失败'))
  } finally {
    busy.value = false
  }
}
/** 分页选择后按真实接口加载十条数据。 */
async function changePage(value: number) {
  page.value = value
  await load()
}
onMounted(load)
</script>
<template>
  <section class="teachers-page">
    <h1>老师账号</h1>
    <p>创建的老师拥有独立教学空间，可管理自己的课程与媒资。</p>
    <form
      class="account-form"
      @submit.prevent="submit"
    >
      <label>
        账号
        <input
          v-model="form.username"
          minlength="3"
          maxlength="45"
          autocomplete="off"
          required
        />
      </label>
      <label>
        姓名
        <input
          v-model="form.name"
          maxlength="45"
          required
        />
      </label>
      <label>
        初始密码
        <input
          v-model="form.password"
          type="password"
          minlength="8"
          maxlength="72"
          autocomplete="new-password"
          required
        />
      </label>
      <label>
        确认密码
        <input
          v-model="form.confirmPassword"
          type="password"
          autocomplete="new-password"
          required
        />
      </label>
      <el-button
        native-type="submit"
        type="primary"
        :loading="busy"
      >
        创建老师账号
      </el-button>
    </form>
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    />
    <el-table
      v-loading="loading"
      :data="rows"
    >
      <el-table-column
        prop="username"
        label="账号"
      />
      <el-table-column
        prop="name"
        label="姓名"
      />
      <el-table-column
        prop="companyName"
        label="教学空间"
      />
      <el-table-column label="状态">
        <template #default="scope">{{ scope.row.status === '1' ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column
        prop="createTime"
        label="创建时间"
      />
    </el-table>
    <el-pagination
      layout="prev,pager,next,total"
      :page-size="10"
      :total="count"
      :current-page="page"
      @current-change="changePage"
    />
  </section>
</template>
<style scoped>
.teachers-page {
  padding: 32px;
  display: grid;
  gap: 24px;
}
.account-form {
  background: white;
  border: 1px solid #e2e8f0;
  padding: 24px;
  border-radius: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
label {
  display: grid;
  gap: 8px;
}
input {
  padding: 12px;
  border: 1px solid #d8dfe8;
  border-radius: 8px;
}
</style>
