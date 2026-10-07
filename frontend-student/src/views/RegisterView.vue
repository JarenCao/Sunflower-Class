<!-- 学员自助注册：只有学员开户字段，成功后进入现有登录流程。 -->
<script setup lang="ts">
import { errorMessage } from '../../../frontend-shared/error-message'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../auth'
const form = reactive({ username: '', name: '', password: '', confirmPassword: '' })
const busy = ref(false)
const error = ref('')
const done = ref(false)
const router = useRouter()
/** 服务端负责唯一性及身份固定，页面负责避免重复提交和显示真实错误。 */
async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await register(form)
    form.password = ''
    form.confirmPassword = ''
    done.value = true
  } catch (failure: any) {
    error.value = errorMessage(failure, '注册失败，请稍后重试')
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <main class="register-page">
    <form
      class="register-card"
      @submit.prevent="submit"
    >
      <h1>注册小葵花课堂学员</h1>
      <template v-if="!done">
        <p>账号为3至45位字母、数字、点、下划线或短横线。密码至少8位。</p>
        <label>
          账号
          <input
            v-model="form.username"
            autocomplete="username"
            minlength="3"
            maxlength="45"
            required
          />
        </label>
        <label>
          姓名
          <input
            v-model="form.name"
            autocomplete="name"
            maxlength="45"
            required
          />
        </label>
        <label>
          密码
          <input
            v-model="form.password"
            type="password"
            autocomplete="new-password"
            minlength="8"
            maxlength="72"
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
        <p
          v-if="error"
          role="alert"
          class="error"
        >
          {{ error }}
        </p>
        <button :disabled="busy">{{ busy ? '注册中…' : '注册' }}</button>
      </template>
      <template v-else>
        <p role="status">注册成功，现在可以使用新账号登录。</p>
        <button
          type="button"
          @click="router.replace('/login')"
        >
          前往登录
        </button>
      </template>
      <RouterLink to="/login">返回登录</RouterLink>
    </form>
  </main>
</template>
<style scoped>
.register-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 40px;
  background: #f4f7f2;
}
.register-card {
  width: 480px;
  padding: 36px;
  border-radius: 20px;
  background: white;
  display: grid;
  gap: 18px;
}
h1 {
  font-size: 26px;
}
p {
  color: #6f8084;
  line-height: 1.6;
}
label {
  display: grid;
  gap: 8px;
}
input {
  padding: 12px;
  border: 1px solid #dbe2e3;
  border-radius: 8px;
}
button {
  padding: 13px;
  background: #263a3d;
  color: white;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
}
.error {
  color: #b83838;
}
</style>
