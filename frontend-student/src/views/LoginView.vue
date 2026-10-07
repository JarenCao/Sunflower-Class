<!-- 登录只连接真实账号接口，不提供演示账号或本地身份。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../auth'

const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
const route = useRoute()
const router = useRouter()
/** 提交期间禁用重复操作；只跳回本站路径，避免外部跳转。 */
async function submit() {
  busy.value = true
  error.value = ''
  try {
    await login(username.value, password.value)
    password.value = ''
    const next = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(
      next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/login') ? next : '/',
    )
  } catch (failure: any) {
    error.value = errorMessage(failure, '登录失败，请稍后重试')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form
      class="login-card"
      @submit.prevent="submit"
    >
      <span class="login-mark">✳</span>
      <h1>欢迎回到小葵花课堂</h1>
      <p>登录，开启今天的学习</p>
      <label>
        账号
        <input
          v-model="username"
          autocomplete="username"
          maxlength="64"
          required
        />
      </label>
      <label>
        密码
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          maxlength="72"
          required
        />
      </label>
      <p
        v-if="error"
        role="alert"
        class="login-error"
      >
        {{ error }}
      </p>
      <button
        type="submit"
        :disabled="busy"
      >
        {{ busy ? '登录中…' : '登录' }}
      </button>
      <RouterLink to="/register">还没有账号？注册学员账号</RouterLink>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: #f4f7f2;
  padding: 24px;
}
.login-card {
  width: min(420px, 100%);
  padding: 40px;
  background: white;
  border-radius: 20px;
  box-shadow: 0 12px 50px #304d2010;
}
.login-mark {
  font-size: 40px;
  color: #8aa348;
}
h1 {
  font-size: 24px;
  margin: 18px 0 10px;
}
p {
  color: #667064;
  margin-bottom: 24px;
}
label {
  display: block;
  margin: 18px 0;
}
input {
  display: block;
  box-sizing: border-box;
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid #dce3d7;
  border-radius: 8px;
  font: inherit;
}
button {
  width: 100%;
  padding: 13px;
  background: #647e37;
  color: white;
  border: 0;
  border-radius: 8px;
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
}
.login-error {
  color: #b33535;
}
</style>
