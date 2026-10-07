# 转码恢复验收单

日期：2026-10-02。状态：技术验证通过，等待 P1 统一验收。

复用 RabbitMQ 消费者、VideoScheduler、FFmpeg 工具及 media 数据库，未增加服务或依赖。待处理 20301→处理中 20304→可用 20302 / 失败 20303。失败提交后确认原消息，定时任务按 retry_at 再投递；默认最多三次失败、间隔 30 秒，耗尽后保留失败记录，机构可人工重启。超时任务恢复有处理权校验；每次处理使用独立临时文件。

## 校验结果

- 本轮真实 FFmpeg 失败和重试 9 项通过：失败一次后清空 processing_at、到期重试、三次后停止、没有第四次、人工重启、学员拒绝、可用媒资不重试、真实绑定仍在。
- 本轮新视频成功及清理 8 项通过：真实 Docker FFmpeg 成功，任务归档恰好一次，保留原始路径，原文件和转码产物均存在；删除后元数据与对象全清。取消任务的实际 Windows 临时目录和 WSL 临时文件也已清空。
- 前序上传→转码→绑定断言通过。保留草稿课程 161、教学小节 350、可用媒资 0eeebb8bae7282f9ed8be4bc3e563435 供验收。
- 浏览器“查看原因/重试”显示失败次数 3 和真实 FFmpeg 失败原因。

## 人工验收

打开 http://localhost:5173/media，查询 p1-corrupt.mp4，点击“查看原因/重试”，确认失败次数与原因。确认重试后应进入有限重试，不能无间隔循环。查询 p1-valid.mp4 应显示转码完成，并可在课程 161 查看绑定。

配置缺省值已可用：media.transcode.max-attempts=3、retry-delay-seconds=30。当时已在本机应用新增列；历史迁移脚本已移除，新环境使用 [六库初始化](sql/INIT_DATABASES.sql)，已有环境需核查 media 实际结构。历史证据路径为 .runtime/p1-transcode-final-current-results.json、p1-transcode-success-delete-results.json，不保证当前仍存在。
