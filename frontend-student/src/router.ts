/* 学员端路由：连接首页、目录、详情及真实学习与订单页面。 */
import { createRouter, createWebHistory } from 'vue-router'
import { loadIdentity } from './auth'
import HomeView from './views/HomeView.vue'
import CatalogView from './views/CatalogView.vue'
import DetailView from './views/DetailView.vue'
import MyCoursesView from './views/MyCoursesView.vue'
import LearningView from './views/LearningView.vue'
import OrdersView from './views/OrdersView.vue'

const router = createRouter({
  history: createWebHistory(),
  // 订单抽屉只改变查询参数，打开和关闭详情都保留列表的滚动位置。
  scrollBehavior: (to, from) => {
    if (to.path === '/orders' && from.path === '/orders') return false
    // 切换到其他页面仍回到顶部，避免继承上一页的阅读位置。
    return { top: 0 }
  },
  routes: [
    { path: '/login', component: () => import('./views/LoginView.vue') },
    { path: '/register', component: () => import('./views/RegisterView.vue') },
    { path: '/institution-apply', component: () => import('./views/InstitutionApplyView.vue') },
    { path: '/', component: HomeView },
    { path: '/courses', component: CatalogView },
    { path: '/courses/:id', component: DetailView },
    { path: '/my-courses', component: MyCoursesView },
    { path: '/learn/:id', component: LearningView },
    // 匿名试学复用播放器，权限由正式发布小节授权决定。
    { path: '/trial/:id/:lessonId', component: LearningView },
    { path: '/orders', component: OrdersView },
  ],
})

// 公开课程继续允许匿名浏览；学习与订单入口要求登录，业务能力由后端再次核查。
router.beforeEach(async (to) => {
  if (to.path === '/login' || to.path === '/register') return true
  try {
    const user = await loadIdentity()
    const protectedPage =
      to.path === '/institution-apply' ||
      to.path === '/my-courses' ||
      to.path.startsWith('/learn/') ||
      to.path === '/orders'
    if (protectedPage && !user) return { path: '/login', query: { redirect: to.fullPath } }
    if (protectedPage && user?.role !== 'student') return '/'
  } catch {
    if (to.path !== '/' && !to.path.startsWith('/courses') && !to.path.startsWith('/trial/'))
      return { path: '/login' }
  }
})
export default router
