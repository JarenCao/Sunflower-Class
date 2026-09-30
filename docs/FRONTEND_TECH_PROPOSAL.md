# 前端技术方案与实现状态

## 应用划分

前端拆成两个可独立启动和部署的 Vue 应用：`frontend-admin/` 面向机构和审核员，`frontend-student/` 面向学员。两者各有路由、构建产物和环境变量。机构端使用 Element Plus 处理表单、列表与教学计划；学员端使用独立的 CSS 视觉体系。课程预览、Freemarker 页面生成和静态化发布页面不在范围内。

## 技术栈

| 用途 | 技术 |
| --- | --- |
| 框架与构建 | Vue 3、TypeScript、Vite 7 |
| 路由与状态 | Vue Router 4、Pinia 3 |
| 请求 | Axios；开发服务器把 `/api` 代理到 Gateway |
| 管理端组件 | Element Plus 2 |
| 质量检查 | `vue-tsc` 与 Vite 构建 |

各应用使用自己的 `package-lock.json`。目前没有配置 ESLint、Prettier、Vitest 或 Playwright，后续可随真实业务流程补充，不应把构建成功视为端到端验收。

## 当前实现

机构端连接真实课程、分类、教学计划、媒资、提交审核与发布接口。支持封面上传、章节改名、视频分片上传、服务端续传检查和转码状态刷新。学员端读取已发布快照列表、详情和目录。两端均不使用浏览器本地存储或演示数据；网关由 `VITE_GATEWAY_TARGET` 配置。

后端通过 `sunflower.company-id` 配置当前开发机构，审核人通过 `sunflower.reviewer-name` 标识；尚未完成登录及审核员角色鉴权。审核通过/驳回、课程删除和教学计划排序已接入，选课、学习资格与支付仍无接口；发布消息保存方法仍待实现。对应页面不模拟业务成功。

启动方法、联调证据和功能边界见 [真实联调说明](LIVE_INTEGRATION.md)。后续范围见 [开发清单](DEVELOPMENT_CHECKLIST.md)。
