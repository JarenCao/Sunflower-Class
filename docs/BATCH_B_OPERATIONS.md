# 第二批运维与恢复

先查看状态，不直接改成功标记。凭据来自Nacos和本机外部配置；不用真实密码作为终端参数或写入日志。

## 指标和人工排查

`python scripts/check-operational-backlog.py`输出并保存发布、支付、转码积压计数。建议本机排查阈值：课程发布待完成超过2分钟；失败/执行耗尽任意一条；支付成功未收到学习回执超过10分钟；视频处理超过配置超时加2分钟或失败任意一条。阈值用于提醒调查，不自动篡改数据。

Redis查看INFO memory、INFO stats、INFO keyspace，并用SCAN遍历sunflower:search:_和sunflower:rate:_。课程缓存TTL为15～20秒，详情404为3～5秒，generation永久保存；限流窗口60秒。不要把generation命中计数当成课程缓存命中率。不要使用FLUSHALL修复单条问题。

ES查看正式索引的`_stats/search,get`；对照资源采样中的查询增量、容器CPU与内存、Java工作集和MySQL连接数。大规模或新部署机器需重新跑基线，不能从本机一万条合成数据推断线上极限。

## 发布同步

机构课程列表点击“同步状态”，确认搜索/学习各阶段及错误。修复Redis、RabbitMQ、ES或Learning故障后，使用既有`POST /content/publication-messages/{id}/retry`恢复本机构未完成事件；持久化payload是真实源，禁止读取新草稿冒充原事件。

数据库一分钟租约到期后允许其他Content实例重新抢占；最多10次投递，耗尽后人工恢复开启新一轮。消费失败进入10秒重试队列，达到10次进入`course.search.failed.queue`或`course.learning.failed.queue`。失败副本供排查，回放使用原持久化事件；不要清空队列，也不要手动把state或阶段标成成功。

## 支付回执

查看orders.payment_event中delivered=0的数量、创建时间和attempts。delivered表示Learning成功回执，不仅是MQ发送成功。恢复MQ/Learning后现有调度继续发送，同一订单依靠Learning幂等消费避免重复资格；故障原因只记录类型。

没有真实支付宝付款证据的订单不得改成已支付，也不得通过SQL插入成功回执。真实交易与异步通知仍按用户要求跳过、保持未完成。

## 转码

媒资列表查看处理状态、失败次数和原因。自动重试次数有上限；修复原文件、Silo或FFmpeg后通过现有媒资“重试”入口恢复。成功媒资不能重复重试，删除中的文件不能恢复成处理中。

处理中超过配置超时加2分钟由原VideoScheduler恢复；旧消费者不能提交新任务的结果。超过30分钟是采集工具的辅助异常指标，实际判断以配置超时为准。不要绕过任务租约、直接标记成功或覆盖转码输出。

## 部署注意

Gateway新增导入common/redis-dev.yaml。Redis不可用时公开搜索、登录、注册、试学在Gateway返回503，已登录用户其他业务路径仍沿原权限处理。Search内部保留受控ES回退。正式环境限制业务服务的外部访问。

反向代理后的连接IP可能是代理地址。生产上线前应按受控代理拓扑配置真实来源解析，复测IP配额，不接受客户端任意X-Forwarded-For；本机Vite代理用户共享连接IP属于开发环境事实。

仅本机开发验收，不执行Linux应用部署。对象存储继续使用pgsty/silo，未切换minio/minio。
