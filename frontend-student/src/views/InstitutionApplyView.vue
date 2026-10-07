<!-- 老师申请复用原审核流程，学员账号保留，审核通过后使用独立老师账号。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import axios from 'axios'
import { onMounted, reactive, ref } from 'vue'
import { csrfHeaders } from '../auth'
const client = axios.create({ baseURL: '/api/auth', timeout: 15000 })
const form = reactive({
  mobile: '',
  intro: '',
  username: '',
  name: '',
  password: '',
  confirmPassword: '',
})
const labels: Record<string, string> = {
  mobile: '手机号',
  intro: '教学简介',
  username: '老师账号',
  name: '姓名',
  password: '老师密码',
  confirmPassword: '确认密码',
}
const limits: Record<string, number> = {
  mobile: 11,
  intro: 512,
  username: 45,
  name: 45,
  password: 72,
  confirmPassword: 72,
}
const rows = ref<any[]>([])
const count = ref(0)
const page = ref(1)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const statuses: Record<string, string> = { '40101': '待审核', '40102': '已通过', '40103': '已驳回' }
/** 当前申请人的资料只由真实接口返回，不缓存密码或审核结论。 */
async function load() {
  try {
    const { data } = await client.get('/institution-applications', {
      params: { pageNo: page.value },
    })
    rows.value = data.items
    count.value = data.count
  } catch (failure: any) {
    rows.value = []
    error.value = errorMessage(failure)
  }
}
async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await client.post('/institution-applications', form, { headers: await csrfHeaders() })
    form.password = ''
    form.confirmPassword = ''
    notice.value = '申请已提交。审核通过后，使用老师账号登录教学管理中心。'
    page.value = 1
    await load()
  } catch (failure: any) {
    error.value = errorMessage(failure)
  } finally {
    busy.value = false
  }
}
async function changePage(next: number) {
  page.value = next
  await load()
}
onMounted(load)
</script>
<template>
  <section class="application-page">
    <h1>申请成为老师</h1>
    <p>提交教学简介和独立老师账号，平台审核通过后即可创建课程。学员账号和学习记录保留。</p>
    <form @submit.prevent="submit">
      <label
        v-for="(label, key) in labels"
        :key="key"
      >
        {{ label }}
        <input
          v-model="form[key as keyof typeof form]"
          :type="String(key).includes('assword') ? 'password' : key === 'email' ? 'email' : 'text'"
          :maxlength="limits[key]"
          :autocomplete="String(key).includes('assword') ? 'new-password' : 'off'"
          required
        />
      </label>
      <button :disabled="busy || rows.some((r) => r.status === '40101')">
        {{ busy ? '提交中…' : '提交申请' }}
      </button>
    </form>
    <p
      v-if="error"
      role="alert"
    >
      {{ error }}
    </p>
    <p
      v-if="notice"
      role="status"
    >
      {{ notice }}
    </p>
    <h2>我的申请</h2>
    <article
      v-for="row in rows"
      :key="row.id"
    >
      <h3>{{ row.adminName }} · {{ statuses[row.status] }}</h3>
      <p>老师账号：{{ row.adminUsername }}</p>
      <p v-if="row.reason">审核说明：{{ row.reason }}</p>
      <p v-if="row.status === '40102'">请使用老师账号登录教学管理中心。</p>
    </article>
    <p v-if="!rows.length">暂无申请</p>
    <button
      :disabled="page === 1"
      @click="changePage(page - 1)"
    >
      上一页
    </button>
    第 {{ page }} 页
    <button
      :disabled="page * 10 >= count"
      @click="changePage(page + 1)"
    >
      下一页
    </button>
  </section>
</template>
<style scoped>
.application-page {
  max-width: 850px;
  margin: 40px auto;
  padding: 24px;
}
form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
label {
  display: grid;
  gap: 8px;
}
input {
  padding: 10px;
  border: 1px solid #dce3d7;
  border-radius: 6px;
  width: 100%;
  box-sizing: border-box;
}
button {
  padding: 10px 18px;
  margin: 12px 8px 12px 0;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
}
article {
  background: #fff;
  padding: 18px;
  margin: 16px 0;
  border-radius: 12px;
}
[role='alert'] {
  color: #b33535;
}
@media (max-width: 600px) {
  form {
    grid-template-columns: 1fr;
  }
}
</style>
