# 真实接口联调说明

## 数据与运行环境

2026-10-01 按用户要求，课程数据库由 `sunflower_class` 改为 `class`，当时 Content 和 Learning 的 Nacos 数据源同步切换到 class；同日后续归属整理已将 Learning 独立切到 learning，Content 继续使用 class，见 [数据库归属说明](DATABASE_OWNERSHIP.md)。`users`、`media`、`system` 库以及 Nacos 的 `sunflower_class` 分组、Java 包名保持原值；见 [数据库改名验收单](ACCEPTANCE_DATABASE_RENAME.md)。

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

| 流程           | 当前行为                                                                                                                               |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| 课程管理       | 列表、筛选、新建、修改、分类、封面上传，保存后重新读取数据库                                                                           |
| 教学计划       | 新建/改名章节及小节、绑定媒资、重新读取绑定结果                                                                                        |
| 媒资           | 普通上传；视频 5 MiB 分片、MD5 校验、服务端检查已上传分片、合并、异步转码及状态刷新                                                    |
| 中断恢复       | 取消后重新选择同一文件，重新计算 MD5 并跳过服务器已有分片；不依赖本地存储                                                              |
| 审核与发布     | 审核、审核历史、发布校验、发布/下架消息同事务落库、可靠发送及两端回执已接通；审核员独立授权已接入                                      |
| 学员浏览       | 首页与课程列表通过独立搜索微服务读取 Elasticsearch，服务端关键词/分类/分页；详情目录读取学习副本；使用五位发布状态，不读取草稿作为商品 |
| 登录身份       | 机构及学员登录、JWT Cookie、网关及业务服务验签、CSRF 校验、刷新恢复身份与退出已接通；机构/审核员/学员角色隔离已接入                    |
| 选课与我的学习 | 免费立即开通、收费待支付、重复请求幂等、当前学生分页记录、过期及下架资格查询已接入                                                     |
| 授权视频播放   | 有效学习资格、发布小节、媒资机构/转码/对象校验后取得最长 60 秒地址，原生 video 支持 Range；直接视频入口拒绝学员绕过                    |
| 尚未开放       | 订单支付、学习进度；媒资删除仍在后续清单                                                                                               |

2026-10-01 登录功能移除了代码中的固定机构配置；机构编号和审核显示名从已验证 JWT 获取，账号和密钥由数据库及外部配置提供。角色授权已完成实现，详见 [登录部署说明](AUTHENTICATION.md) 和 [已确认验收单](ACCEPTANCE_AUTHENTICATION.md)。机构文件查看接口返回五分钟签名 URL；学员播放使用独立的资格校验入口和最长 60 秒地址，见 [播放部署说明](COURSE_PLAYBACK.md)；匿名仅允许读取正式发布课程引用的图片封面。历史课程封面能否展示取决于原对象是否存在。课程预览不在范围内。

2026-09-30 新增 Search（63060）和 Learning（63070）。两端现在依赖这两个独立服务；先部署副本表及 Nacos 路由，再启动服务。初始化、消息状态和故障恢复见 [课程消息与下游服务](COURSE_MESSAGE_SERVICES.md)。下架在下游消费完成后隐藏，消息同步采用最终一致性。

## 验证记录

- 两端通过 TypeScript 检查与生产构建。
- `FrontendFlowTest` 覆盖教学计划字段映射、审核与发布状态分离、未审核发布拒绝、已审核发布快照。
- 经网关实际验证课程创建、修改、目录、媒资绑定与提交审核。
- 实际上传 8.5 MB 视频两片，验证分片存在性、合并后文件检查及 RabbitMQ 转码完成状态。
- 联调保留课程 `124`（名称以“联调验证课程”开头）、一张测试图片及视频 `565a3d2de0ae1048a5bcd0d5302b573e`，方便人工核对；未修改现有课程的审核结果。

2026-10-01 选课与我的课程沿用 Learning 三层服务，当时新增 class.course_enrollment，现已连同 learning_course 迁入 learning 库，并复用已有 701/702/703 字典。两门验收课程 159（免费）及 160（收费）保留，接口与边界见 [选课部署](COURSE_ENROLLMENT.md) 和 [已确认验收记录](ACCEPTANCE_ENROLLMENT.md)。
