# 受保护视频播放

## 实现与部署

沿用 API → Service → Model 三层结构，扩展已有 LearningCourseService、MediaFileService 与控制器；DTO 分别在各自 model 模块。复用 JWT、Spring Security、JDBC、Jackson、服务发现、RestClient 和 MinIO 原生签名能力，无新依赖、数据库迁移或 Nacos 配置。

停止项目后端后安装 base、learning、media，再重启相关服务，避免覆盖运行中的共享 JAR：

```powershell
mvn -f base/pom.xml install
mvn -f learning/pom.xml install
mvn -f media/pom.xml install
```

学员端执行 `npm run build`，开发入口仍为 `http://localhost:5174`。Gateway 复用原有 `/learning/**`、`/media/**` 路由。Learning、Media 直接端口同样校验身份与角色。

## 授权流程

1. 学员页面查询当前学生选课资格，只有 70301 才加载课程和小节。
2. Media 转发已经验签的原 JWT，请 Learning 验证当前学生资格、有效期、发布状态，以及小节确实属于该课程的正式发布快照。
3. Learning 仅返回发布快照里的媒资 ID 和机构编号。请求不能指定或替换媒资、机构或学生身份。
4. Media 验证媒资存在、属于发布机构、类型为视频 20102、转码状态为完成 20302，并实际检查对象存在。
5. 使用转码对象生成 GET 签名 URL，有效期不超过 60 秒，也不超过当前 JWT 剩余有效期。授权响应使用 Cache-Control: no-store。

学员端使用浏览器原生 video，支持对象存储的 Range 请求；刷新和切换小节都会重新授权。地址只在当前页面内存中保存，离开或切换时清空；失败可点击“重新获取视频”。不持久化地址，不生成虚假学习进度。

## 接口

| 路径                                         | 返回                                                                   |
| -------------------------------------------- | ---------------------------------------------------------------------- |
| GET /media/playback/{courseId}/{lessonId}    | courseId、lessonId、url、expiresAt；学员端使用 /api 前缀               |
| GET /learning/playback/{courseId}/{lessonId} | 已授权发布小节的 courseId、lessonId、mediaId、companyId，供 Media 调用 |

未登录 401；非学员、未选课、未支付、过期或下架 403；课程/小节不匹配、媒资不存在、机构或类型不匹配、对象不存在 404；未完成转码 409；Learning 或对象存储不可用 503。普通文件查看接口不向学员开放视频，公开目录不能代替播放资格。

## 目前边界

收费课程仍为待支付，支付开通按后续清单实现。发布/下架状态复用 RabbitMQ 学习副本，采用最终一致性。每次申请新地址重新检查资格；已签发地址在其短期有效期内仍可读取，已下载或缓冲的数据不能即时撤回。地址过期后的新请求可能失败，可重新获取；此实现不提供 DRM 或学习进度记录。

验收见 [视频播放验收单](ACCEPTANCE_PLAYBACK.md)。
