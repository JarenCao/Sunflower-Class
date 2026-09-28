# 小葵花课堂本次验收

## 已完成

1. 学员搜索对标题和标签统一进行 NFKC 规范化、去首尾空格和大小写转换；小写、大写、全角英文均能匹配。
2. 两端导航、页脚、首页介绍和浏览器标题统一显示“小葵花课堂”。代码包名、Maven 坐标和仓库目录保持原有标识。
3. 后端业务码集中为 BusinessCodes；无效等级、教学模式和收费编码被拒绝。收费课程仍必须填写正价格。
4. 机构 ID 从 sunflower.company-id 读取；Nacos 地址和命名空间可配置；日志目录不再绑定个人磁盘路径。FFmpeg 实际读取配置类的镜像、目录、编码参数，转码桶使用 MinioConfig。
5. 课程、快照、教学计划及媒资业务状态完成五位编码迁移，已有课程及文件保留。迁移与回滚说明见 [状态编码规范](STATUS_CODES.md)。
6. 修复定时补偿和消费者的重试上限冲突；状态失败、处理中、可用均有明确显示。

## 自动与真实环境验证

- 两端 npm run build 成功，包含 TypeScript 检查。机构端现有主包体积提示仍存在，不影响构建。
- 内容服务：FrontendFlowTest 4 项、CourseCodeValidationTest 2 项通过。
- 媒资服务：MediaStatusTest 2 项通过，覆盖成功归档和配置上限耗尽后的失败状态。
- 数据库迁移预检及迁移后 20 个字段编码检查通过，原始数据备份保留。
- 页面实际输入 nacos 及带全角空格的 ＮＡＣＯＳ，均显示 Nacos微服务开发实战。
- 课程 117 页面实际显示中级、录播、收费；五门原发布课程仍可查询，返回统一编码。
- 新上传 status-acceptance.mp4，经 RabbitMQ 和 Ubuntu Docker FFmpeg 完成转码，最终状态 20302。媒资 ID：a2ad3a747a635b41e992457ba9017e12。
- 实际通过课程 124 保存标准五位编码；旧收费编码 201001 被接口拒绝。验收视频通过 Silo 签名链接读取，Range 请求返回 HTTP 206。
- Nacos 的 media-service-dev.yaml 中 ffmpeg.host-data-dir 已从旧 Windows 路径改为 /tmp/xiaokuihua-ffmpeg。

## 你可以这样验收

1. 打开学员端 http://127.0.0.1:5174/courses，依次搜索 nacos、NACOS、空格加 NaCoS、全角 ＮＡＣＯＳ；预期都能找到同一课程。清空后恢复课程列表。
2. 检查两个页面的标签页标题、导航和学员页脚，名称应为“小葵花课堂”。
3. 打开机构端 http://127.0.0.1:5173/courses/117，检查等级、模式和收费显示，不再出现“历史数据”重复选项。
4. 打开 http://127.0.0.1:5173/media，搜索 status-acceptance，预期显示“转码完成”，打开文件应能读取真实视频。
5. 在课程编辑页保存标准五位编码；直接提交旧收费编码 201001 应被拒绝，不再绕过价格校验。

## 主要配置入口

| 配置 | 默认或用途 |
| --- | --- |
| NACOS_SERVER_ADDR | localhost:8848；WSL 映射不可用时设置实际 Ubuntu 地址 |
| NACOS_NAMESPACE | dev |
| SUNFLOWER_COMPANY_ID | 1232141425；当前单机构开发身份，不是登录认证 |
| LOG_PATH | logs；相对服务进程工作目录 |
| ffmpeg.host-data-dir | Ubuntu Docker 可访问的绝对目录 |
| ffmpeg.wsl-distro | Ubuntu-24.04 |
| ffmpeg.image / video-codec / audio-codec / crf / preset | FFmpeg 参数，Nacos 可覆盖 |
| minio.videofiles | video；上传与转码共用配置桶 |
| media.transcode.max-attempts | 3；消费者与补偿一致 |
| media.transcode.retry-batch-size / retry-cron | 10 / 每五分钟 |

本次不增加登录、选课、支付或审核通过接口；这些仍按原开发清单推进。
