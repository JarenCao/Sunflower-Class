/* 机构端路由：按访问页面懒加载组件，新建与编辑共用课程编辑页。 */
import { createRouter, createWebHistory } from 'vue-router'

export default createRouter({
  history: createWebHistory(),
  routes: [
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
