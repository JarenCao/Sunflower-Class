# 真实接口联调说明

## 数据与运行环境

两个前端已移除演示数据和浏览器持久化。课程、目录与媒资元数据写入 WSL2 Ubuntu Docker 的 MySQL；文件写入 Silo 的 S3 兼容存储；视频转码使用 RabbitMQ 和 Docker FFmpeg。

本机检查到发行版 `Ubuntu-24.04`，容器名为 `mysql`、`nacos`、`rabbitmq`、`silo-server`（Silo）。Nacos 保存数据库、RabbitMQ 和对象存储配置，不要把密码放入前端环境变量。WSL 地址变化时需同步 Nacos 中的服务连接地址。签名文件链接要求浏览器也能访问 Silo endpoint。

## 启动顺序

先保持 Ubuntu 运行，并在其中执行 `docker start mysql nacos rabbitmq silo-server`。按 AGENTS.md 安装 parent、base、content、media 构件，再分别启动 Gateway、Content、Media；不要在运行中覆盖依赖 JAR，DevTools 可能在安装中途重启失败。

本机 Windows JDK 的默认临时目录曾造成套接字初始化失败。每个后端终端从仓库根目录执行以下准备命令，再启动对应服务：

```powershell
New-Item -ItemType Directory -Force .runtime/tmp | Out-Null
$env:TEMP=(Resolve-Path .runtime/tmp).Path
$env:TMP=$env:TEMP
mvn -f content/content_api/pom.xml spring-boot:run
```

其他终端将 POM 替换为 `gateway/pom.xml`、`media/media_api/pom.xml`。端口分别为 63010、63040、63050。两个前端各自执行 `npm ci`、`npm run dev`，端口 5173、5174；`/api` 由 Vite 代理到 Gateway。

## 已接通与边界

| 流程 | 当前行为 |
| --- | --- |
| 课程管理 | 列表、筛选、新建、修改、分类、封面上传，保存后重新读取数据库 |
| 教学计划 | 新建/改名章节及小节、绑定媒资、重新读取绑定结果 |
| 媒资 | 普通上传；视频 5 MiB 分片、MD5 校验、服务端检查已上传分片、合并、异步转码及状态刷新 |
| 中断恢复 | 取消后重新选择同一文件，重新计算 MD5 并跳过服务器已有分片；不依赖本地存储 |
| 审核与发布 | 提交审核及发布接口已接通；通过/驳回接口缺失，不能完成新课程审核全闭环；发布消息保存方法仍为空 |
| 学员浏览 | 新增已发布快照列表和详情查询；使用迁移后的五位发布状态；不读取草稿作为商品；编码迁移见 STATUS_CODES.md |
| 尚未开放 | 删除、排序、登录鉴权、选课、学习资格、订单支付、学习进度、授权播放、搜索索引同步 |

`sunflower.company-id` 默认 `1232141425`，用于当前单机构开发联调，不能代替认证或完整机构权限检查。文件查看接口返回五分钟签名 URL，不能充当学员付费播放授权。历史课程封面能否展示取决于原对象是否存在。课程预览不在范围内。

## 验证记录

- 两端通过 TypeScript 检查与生产构建。
- `FrontendFlowTest` 覆盖教学计划字段映射、审核与发布状态分离、未审核发布拒绝、已审核发布快照。
- 经网关实际验证课程创建、修改、目录、媒资绑定与提交审核。
- 实际上传 8.5 MB 视频两片，验证分片存在性、合并后文件检查及 RabbitMQ 转码完成状态。
- 联调保留课程 `124`（名称以“联调验证课程”开头）、一张测试图片及视频 `565a3d2de0ae1048a5bcd0d5302b573e`，方便人工核对；未修改现有课程的审核结果。
