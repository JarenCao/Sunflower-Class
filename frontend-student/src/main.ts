/* 学员端启动：注册 Pinia、路由和全局样式后挂载应用。 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './style.css'
createApp(App).use(createPinia()).use(router).mount('#app')
