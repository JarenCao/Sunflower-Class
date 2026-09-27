/* 学员端路由：连接首页、目录、详情及待接入的学习与订单页面。 */
import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import CatalogView from './views/CatalogView.vue'
import DetailView from './views/DetailView.vue'
import MyCoursesView from './views/MyCoursesView.vue'
import LearningView from './views/LearningView.vue'
import OrdersView from './views/OrdersView.vue'

export default createRouter({
  history: createWebHistory(),
  // 切换页面后滚动到顶部，避免继承上一页的阅读位置。
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView },
    { path: '/courses', component: CatalogView },
    { path: '/courses/:id', component: DetailView },
    { path: '/my-courses', component: MyCoursesView },
    { path: '/learn/:id', component: LearningView },
    { path: '/orders', component: OrdersView },
  ],
})
