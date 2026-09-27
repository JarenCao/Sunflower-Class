<!-- 学员端应用框架：品牌导航、路由页面和公共页脚。 -->
<script setup lang="ts"></script>
<template>
  <div class="student-shell">
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
        </nav>
        <div class="header-right">
          <RouterLink
            to="/orders"
            class="header-order"
          >
            我的订单
          </RouterLink>
          <span class="student-avatar">学</span>
        </div>
      </div>
    </header>
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
