# 机构端与审核工作台

Vue 3、TypeScript、Vite、Vue Router、Element Plus、Axios；SparkMD5 用于分片上传校验。

```sh
npm ci
npm run dev
npm run build
```

默认端口 5173。全部业务数据从后端读取，不使用 localStorage、sessionStorage 或演示数据。通过 `.env.local` 的 `VITE_GATEWAY_TARGET` 设置网关地址，默认 `http://localhost:63010`。

已接通：课程列表与筛选、新建和编辑、分类、封面上传、章节和小节新建与改名、课程编辑页就地上传及绑定媒资、提交审核、审核通过/驳回及记录、发布接口、媒资列表、普通上传、视频分片上传与服务端续传、转码状态刷新、文件查看。

审核工作台可按待审、通过、驳回状态分页查看课程并读取历史；驳回原因必填。发布需要已审核通过的预发布记录，发布消息落库仍待完成。当前采用后端 `sunflower.company-id` 开发机构配置和 `sunflower.reviewer-name` 审核人标识，不代表已具备登录、审核员角色与权限控制。详见 [联调说明](../docs/LIVE_INTEGRATION.md)。

界面仅面向电脑端使用，不维护手机、平板专用布局；保留桌面窗口缩放所需的列数与间距调整。
