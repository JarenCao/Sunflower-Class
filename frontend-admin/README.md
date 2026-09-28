# 机构端与审核工作台

Vue 3、TypeScript、Vite、Vue Router、Element Plus、Axios；SparkMD5 用于分片上传校验。

```sh
npm ci
npm run dev
npm run build
```

默认端口 5173。全部业务数据从后端读取，不使用 localStorage、sessionStorage 或演示数据。通过 `.env.local` 的 `VITE_GATEWAY_TARGET` 设置网关地址，默认 `http://localhost:63010`。

已接通：课程列表与筛选、新建和编辑、分类、封面上传、章节和小节新建与改名、媒资绑定、提交审核、发布接口、媒资列表、普通上传、视频分片上传与服务端续传、转码状态刷新、文件查看。

审核工作台目前只能查询待审课程；后端尚无通过/驳回接口。删除、排序接口也未实现。发布需要已审核通过的预发布记录，发布消息落库仍待完成。当前采用后端 `sunflower.company-id` 开发机构配置，不代表已具备登录和权限控制。详见 [联调说明](../docs/LIVE_INTEGRATION.md)。

界面仅面向电脑端使用，不维护手机、平板专用布局；保留桌面窗口缩放所需的列数与间距调整。
