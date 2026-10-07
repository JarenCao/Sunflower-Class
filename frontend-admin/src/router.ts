/* 机构端路由：按访问页面懒加载组件，新建与编辑共用课程编辑页。 */
import { createRouter, createWebHistory } from 'vue-router'
import { loadIdentity } from './auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./views/LoginView.vue') },
    { path: '/teachers', component: () => import('./views/TeachersView.vue') },
    { path: '/institution-review', component: () => import('./views/InstitutionReviewView.vue') },
    // 按需加载教学工作台。
    { path: '/', component: () => import('./views/DashboardView.vue') },
    // 按需加载课程分页管理页。
    { path: '/courses', component: () => import('./views/CoursesView.vue') },
    // 按需加载新建课程表单。
    { path: '/courses/new', component: () => import('./views/CourseEditorView.vue') },
    // 复用编辑组件，按路径编号读取课程。
    { path: '/courses/:id', component: () => import('./views/CourseEditorView.vue') },
    // 按需加载媒资中心与上传功能。
    { path: '/media', component: () => import('./views/MediaView.vue') },
    // 按需加载待审核课程列表。
    { path: '/review', component: () => import('./views/ReviewView.vue') },
  ],
})

// 每次进入管理页面重新核对 Cookie 身份；匿名访问保留原目标路径。
router.beforeEach(async (to) => {
  if (to.path === '/login') return true
  try {
    const user = await loadIdentity()
    if (!user) return { path: '/login', query: { redirect: to.fullPath } }
    // 三种身份由后端确定：管理员统一审核和开户，老师只维护教学资料。
    const platformPages = ['/review', '/institution-review', '/teachers']
    if (user.role === 'admin') return platformPages.includes(to.path) ? true : '/review'
    if (user.role !== 'teacher' || !user.companyId) return { path: '/login' }
    if (platformPages.includes(to.path)) return '/'
  } catch {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})
export default router
