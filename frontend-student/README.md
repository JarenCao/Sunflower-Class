# 学员端

Vue 3、TypeScript、Vite、Vue Router 和 Axios，默认端口 5174。

```sh
npm ci
npm run dev
npm run build
```

通过网关读取 `/content/published-courses` 与 `/content/published-courses/{id}` 的真实发布快照，支持课程列表、筛选、详情和目录。不使用浏览器本地存储或样例课程；接口失败会展示错误。`.env.local` 可设置 `VITE_GATEWAY_TARGET`，默认 `http://localhost:63010`。

登录、选课、我的课程、支付和授权视频播放尚无后端接口，对应页面保留明确的未开放说明，不创建虚假订单或学习记录。不提供课程预览。详见 [联调说明](../docs/LIVE_INTEGRATION.md)。

界面仅面向电脑端使用，不维护手机、平板专用布局；保留桌面窗口缩放所需的列数与间距调整。
