/// <reference types="vite/client" />
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

/* 学员端的 Vite 环境类型声明，供 TypeScript 识别前端构建环境。 */
