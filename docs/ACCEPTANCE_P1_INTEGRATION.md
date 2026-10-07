# P1 统一验收单

日期：2026-10-02。状态：P1 全部技术开发与逐项验证完成，等待用户统一验收。按用户最新要求，每项验证通过后才推进下一项；本次不分项等待人工验收。

## 完成范围

| P1 项目            | 结果与证据                                                                                      |
| ------------------ | ----------------------------------------------------------------------------------------------- |
| 学员自主注册       | 注册→登录→真实选课播放；[注册验收单](ACCEPTANCE_REGISTRATION.md)                                |
| 管理员创建老师     | 事务保存账号/机构/老师角色，权限与机构隔离；[老师验收单](ACCEPTANCE_TEACHER_ACCOUNTS.md)        |
| 登录、独立认证服务 | Auth 三层，users 数据库，JWT 与 CSRF；[认证服务验收单](ACCEPTANCE_AUTH_SERVICE.md)              |
| 角色权限           | 机构、老师、审核员、学员权限隔离；[角色验收单](ACCEPTANCE_ROLES.md)                             |
| 课程发现           | Search 三层、Elasticsearch、分页与正式快照；[服务验收单](ACCEPTANCE_COURSE_MESSAGE_SERVICES.md) |
| 选课与我的学习     | 免费取得资格，收费待支付，身份隔离及幂等；[选课验收单](ACCEPTANCE_ENROLLMENT.md)                |
| 目录与受保护播放   | Learning / Media 核查课程、资格、正式小节，签名 MP4 Range；[播放验收单](ACCEPTANCE_PLAYBACK.md) |
| 普通上传与断点续传 | 已完成机构分片隔离、完整性与失败清理；[上传验收单](ACCEPTANCE_UPLOAD_RESUME.md)                 |
| 转码恢复           | 有限自动重试、人工重启、超时恢复、成功归档；[转码验收单](ACCEPTANCE_TRANSCODE_RETRY.md)         |
| 媒资安全删除       | 引用拦截、真实任务取消、并发保护、失败恢复；[删除验收单](ACCEPTANCE_MEDIA_DELETE.md)            |

收费课程在 P1 仅保留待支付选课，不开通播放资格。订单、支付成功开通资格和机构入驻属于 P2，等待后续开发。

## 本轮验证

本轮新增/补验 83 项实时断言通过：账号 19、失败转码重试 9、删除边界/恢复 21、取消/并发 13、成功转码/清理 8、完整学习回归 13。另有两个只读 BCrypt 格式核查。前序注册创建 9 项、老师创建 14 项及原 P1 上传/角色/选课/播放验证结果保留在各项验收单。

Auth、Content、Media 的 Java 21 Maven 编译和已有检查通过；两套前端 vue-tsc 与 Vite 生产构建通过。Auth/Content/Media/Gateway/Search/Learning 与两套 Vite 运行，使用真实 Nacos、MySQL、RabbitMQ、MinIO、Docker FFmpeg、Elasticsearch。

中文注释和文档使用 UTF-8；仓库文本严格 UTF-8 解码及替换乱码字符扫描通过，P1 涉及源码格式检查通过。system.dictionary 已补 20305 删除中。class / media / users / learning 各自归属未混写。

所有专用 p1-delete-* 测试文件、对象和临时教学节点已清理；原媒资完整性快照比对通过。保留 P1 学员/老师账号和课程 161、小节 350、p1-valid.mp4 / p1-corrupt.mp4 供验收。随机密码仅在本机 .runtime/p1-new-accounts.json；原管理员密码仅在 .runtime/login-accounts.json，不写入文档、日志或源码。

## 建议验收顺序

1. http://localhost:5174/register：注册、登录，重复账号和密码不一致应拒绝。
2. http://localhost:5174/my-courses：免费课程 159 可以学习并播放，收费课程 160 待支付不能播放。
3. http://localhost:5173/teachers：管理员创建老师；老师可登录本机构工作区，不能管理账号或审核课程。
4. http://localhost:5173/media：失败原因/重试、上传视频转码、课程绑定。
5. 同一媒资页：已绑定文件提示引用且不能删除；新未绑定文件取消删除保留、确认删除完成清理。

验收时同一浏览器的 localhost 登录 Cookie 会在两个端之间共享，切换机构/学员身份应先退出再登录；需要同时打开不同身份可用浏览器独立会话。

## 真实页面证据

![学员注册](../.runtime/p1-proof/register.jpg)

![老师账号管理](../.runtime/p1-proof/teachers.jpg)

![引用拦截](../.runtime/p1-proof/references.jpg)

![删除二次确认](../.runtime/p1-proof/delete-confirm.jpg)

![新注册学员选课](../.runtime/p1-proof/learning.jpg)

![真实视频目录](../.runtime/p1-proof/playback.jpg)

临时脚本、断言 JSON、截图及敏感账号文件在忽略目录 .runtime 内，仅用于本机验收。用户确认前不记为人工验收通过，不推进 P2。
