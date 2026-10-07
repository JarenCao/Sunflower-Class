# 老师与平台管理员工作台

Vue 3、TypeScript、Vite、Vue Router、Element Plus、Axios；SparkMD5 用于分片上传校验。默认端口 5173，通过同源 `/api` 代理 Gateway，`.env.local` 的 `VITE_GATEWAY_TARGET` 默认指向 `http://localhost:63010`。

```sh
npm ci
npm run dev
npm run build
```

老师管理本人教学空间的课程、目录、讲师介绍及媒资，支持封面和分片上传、转码状态、媒资引用和删除、提审、发布、下架及同步消息重试。平台管理员使用课程审核、老师申请审核、老师账号三个入口；老师不能自行审核或创建老师账号。

身份来自后端登录 Cookie，归属来自服务端已验签 JWT，不使用写死的开发机构或审核人配置，不使用浏览器存储或演示数据。当前数据库已清空时，先按 [项目梳理](../docs/PROJECT_GUIDE.md) 初始化结构和首个管理员，再开户使用。

中文错误处理复用 `frontend-shared/error-message.ts`；页面负责展示和操作语境。构建执行 TypeScript 检查，真实业务还需要后端和基础设施验证。

界面面向电脑端，保留桌面窗口缩放所需的布局调整。
