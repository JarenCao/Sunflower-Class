<!-- 学员端应用框架：品牌导航、路由页面和公共页脚。 -->
<script setup lang="ts">
import { useRoute } from 'vue-router'
import { ref } from 'vue'
import { errorMessage } from '../../frontend-shared/error-message'
import { identity, logout } from './auth'
const route = useRoute()
const logoutError = ref('')
/** 退出后重新加载公开首页，清空当前用户的页面状态。 */
async function signOut() {
  logoutError.value = ''
  try {
    await logout()
    window.location.assign('/')
  } catch (error) {
    logoutError.value = errorMessage(error, '退出失败，请稍后重试')
  }
}
</script>
<template>
  <RouterView v-if="route.path === '/login'" />
  <div
    v-else
    class="student-shell"
  >
    <div class="announcement">
      <span>✦</span>
      每一点热爱，都值得被认真对待
      <span>✦</span>
    </div>
    <header class="site-header">
      <div class="header-inner">
        <RouterLink
          to="/"
          class="logo"
        >
          <span class="logo-mark">✳</span>
          <span>
            小葵花课堂
            <small>LEARN WITH SUNSHINE</small>
          </span>
        </RouterLink>
        <nav>
          <RouterLink to="/">首页</RouterLink>
          <RouterLink to="/courses">全部课程</RouterLink>
          <RouterLink to="/my-courses">我的学习</RouterLink>
          <RouterLink to="/institution-apply">申请成为老师</RouterLink>
        </nav>
        <div class="header-right">
          <RouterLink
            to="/orders"
            class="header-order"
          >
            我的订单
          </RouterLink>
          <template v-if="identity">
            <span>{{ identity.name }}</span>
            <button
              type="button"
              @click="signOut"
            >
              退出
            </button>
          </template>
          <RouterLink
            v-else
            to="/login"
          >
            登录
          </RouterLink>
        </div>
      </div>
    </header>
    <p
      v-if="logoutError"
      role="alert"
      class="logout-error"
    >
      {{ logoutError }}
    </p>
    <main><RouterView /></main>
    <footer class="site-footer">
      <div>
        <RouterLink
          to="/"
          class="logo footer-logo"
        >
          <span class="logo-mark">✳</span>
          <span>
            小葵花课堂
            <small>LEARN WITH SUNSHINE</small>
          </span>
        </RouterLink>
        <p>每一天，向着更好的自己生长。</p>
      </div>
      <div class="footer-links">
        <RouterLink to="/courses">探索课程</RouterLink>
        <RouterLink to="/my-courses">我的学习</RouterLink>
        <RouterLink to="/orders">我的订单</RouterLink>
      </div>
      <small>© 2026 小葵花课堂 · 学习，让未来有更多可能</small>
    </footer>
  </div>
</template>

<style scoped>
.logout-error {
  color: #b42318;
  text-align: center;
}
</style>
