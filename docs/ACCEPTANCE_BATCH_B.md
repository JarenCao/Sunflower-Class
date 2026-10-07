# 第二批开发与验收记录

2026-10-04，范围为 DEVELOPMENT_CHECKLIST 中 B01～B05。技术校验完成后等待用户验收；不代表用户已验收。真实支付宝付款按既有要求继续跳过，A01/P2 支付闭环仍未完成。

## B01：性能基线

复用真实服务、Python 标准库和现有验收账号；没有新增压测依赖。Windows 主机约16 GiB内存、20逻辑处理器，WSL容器可用内存约5.79 GiB；正式索引8门已发布课程。测试结果只代表当前本机和数据规模。

| 场景                              | 并发/持续时间 | 请求数 | 吞吐量/秒 | P50/P95/P99 毫秒   | 错误 |
| --------------------------------- | ------------- | ------ | --------- | ------------------ | ---- |
| 热门详情，经Vite与Gateway         | 8/20秒        | 3483   | 173.93    | 44.41/68.62/83.49  | 0    |
| 课程列表，覆盖一次15秒到期        | 8/20秒        | 3448   | 172.07    | 44.65/69.55/83.48  | 0    |
| Spring关键词搜索                  | 8/20秒        | 3638   | 181.55    | 42.87/66.84/77.99  | 0    |
| 分类聚合                          | 8/20秒        | 2152   | 107.25    | 74.32/99.74/114.97 | 0    |
| 不存在课程，预期404               | 8/20秒        | 3536   | 176.50    | 44.35/68.63/78.69  | 0    |
| 正确登录，直接Auth含BCrypt        | 4/10秒        | 590    | 58.61     | 69.07/79.63/81.74  | 0    |
| 有效选课播放授权，经Vite与Gateway | 4/10秒        | 994    | 99.27     | 38.27/59.86/66.63  | 0    |

搜索前测在限流上线前完成。后续纯服务容量复测使用Search直连，网关的拒绝边界单独校验，避免把预期429计成业务故障。localhost曾出现约2秒连接回退耗时，因此压测使用127.0.0.1；这不是课程业务耗时。

本机回归预算：上述并发下搜索/详情/分类P95不超过250ms，登录和播放授权P95不超过500ms，业务错误为0。它们是本轮回归检查预算，不是生产容量承诺或线上SLA；正式目标需按部署机器和用户量重新压测。

缓存改动后的20秒资源采样：8并发、4910次搜索/详情请求，只产生2次ES搜索和2次ES文档读取；Redis命中计数增9820，未命中增4。命中计数包含generation读取，不等于业务命中率。保留5次容器CPU/内存、Java累计CPU/工作集与数据库连接采样。末次MySQL连接41、运行线程2、历史最大连接61；Redis约6MiB，ES约2.54GiB，Java工作集合计约3.05GiB（采样当时包含临时第二实例）。

证据：`.runtime/batch-b-ipv4-baseline.json`、`batch-b-auth-playback-baseline.json`、`batch-b-resource-snapshot.json`。首次播放基线误用了第三批故意到期的学员，403未计作成功；最终改用既有有效选课学员，未改变第三批到期数据。

## B02：缓存保护

在现有CourseSearchServiceImpl最小修改：正常缓存15～20秒随机TTL；真实详情404缓存3～5秒；按固定64个本机锁合并同键回源，等待最多250ms；同时限制单实例回源16个，繁忙返回中文503。缓存锁不新增第三方库，不随课程数量无限增长。

锁是每个Search实例本地合并，多实例冷缓存最多各回源一次；不是分布式锁。发布前后推进代数的原协议不变，负缓存也位于代数下；学习资格仍实时验证。基础设施异常不写成404。

11项真实校验通过：32次同键冷缓存请求只发生一次ES详情回源；短TTL、404语义和不访问ES、代数推进隔离等。真实Redis停机演练：Gateway约650ms返回503；Search约27ms回退ES；容器恢复后Gateway约110ms返回200。没有停用认证或伪造数据。

证据：`.runtime/batch-b-cache-check.json`、`batch-b-redis-outage.json`。

## B03：搜索容量

独立索引`sunflower_benchmark_b03_20261004`保存10000条合成课程，复用正式索引的wildcard字段、包含匹配、精确总数与版本排序。8并发，各20秒，英文/中文/不存在/短关键词四场景均零错误；P95为30.76、28.24、25.89、28.24ms。

当前证据不要求修改搜索索引或查询。No code changes are required for this task. 保留原包含匹配语义，没有静默改分词或相关性排序，也没有切换正式索引。容量脚本是验证工具；隔离索引留作重复验收，正式索引未混入合成数据。

证据：`.runtime/batch-b-search-capacity.json`。

## B04：公开接口防滥用

Gateway复用Spring原生响应式Redis客户端，Lua原子计数、首次计数设置60秒过期；真实连接IP按接口组共享窗口。登录20次/分钟、注册5次/分钟、搜索2400次/分钟、匿名试学120次/分钟。超过返回中文429和Retry-After；Redis故障最多等待1秒，返回503，不无限放行。账号层继续复用原Auth数据库锁定：错误5次后锁定15分钟，不新增平行账号锁。

不信任客户端X-Forwarded-For。当前本机Vite代理后的请求共享代理连接IP；生产若有受控反向代理，必须按真实网络拓扑配置可信来源解析并复测，不能直接信任任意转发头。业务服务端口应只向网关和内部服务开放，避免绕过入口保护。

真实搜索、登录、注册、试学分别校验配额内、超额、伪造转发IP和第二网关共享配额，正常搜索恢复。测试计数在finally还原，不锁定用户账号。当前没有需要验证码的产品场景，因此没有新增验证码服务。

证据：`.runtime/batch-b-rate-limit-check.json`。网关运行classpath曾混入测试用MVC依赖，已改用Maven运行范围重建，本机服务已恢复；没有开启Bean覆盖规避冲突。

## B05：多实例和恢复

真实第二Content实例63041和第二Search实例63061共用数据库/队列，但关闭服务发现，不接入用户流量。两个调度实例只抢占一次，搜索与学习回执均成功；重复投递10次不改变完成状态。故障消息以已达到第10次的测试头进入真实失败队列，保留原消息，再通过既有机构恢复接口重投持久化正确快照，已有学习成功回执保留。

专用事件75：第二Content抢占后、投递确认前真实退出，数据库状态为`0:1:0`。主实例恢复后在原一分钟租约到期后重新抢占，最终`1:2:1:1:1`：总成功、两次抢占、投递/搜索/学习阶段成功。恢复脚本的后台进程管道等待已修复，最终状态经独立只读观察验证。RabbitMQ停顿已解除，主服务运行，临时第二实例退出。

仅复制专用验收课程163的既有正式快照，没有伪造收费付款或学习资格；测试事件72/73/75保留可追溯。失败队列中故障副本保留供验收，没有清空他人的消息。

关键积压采集工具见`scripts/check-operational-backlog.py`，当前发布待完成/失败/耗尽、支付未回执、转码失败/处理中均为0。恢复操作见[B批运维说明](BATCH_B_OPERATIONS.md)。本次仍是Windows应用、WSL基础设施；没有Linux应用部署需求证据，因此不改FFmpeg部署路径、不引入任务平台。

证据：`.runtime/batch-b-message-check.json`、`batch-b-dispatch-crash.json`、`batch-b-operational-backlog.json`。

## 重复校验和用户验收

从仓库根目录运行Python脚本。缓存/限流/资源校验需要忽略目录中的`p2-redis-connection.json`（host、port、password）；认证/播放需要既有`login-accounts.json`；双实例消息演练需要现有专用课程和教师账号。这些凭据不提交。容量脚本仅需要本机ES。

```powershell
python scripts/benchmark-search.py --seconds 20 --concurrency 8
python scripts/benchmark-search-capacity.py
python scripts/benchmark-auth-playback.py
python scripts/benchmark-resource-snapshot.py
python scripts/check-search-cache.py
python scripts/check-public-rate-limit.py
python scripts/check-operational-backlog.py
python scripts/check-utf8.py
```

Redis停机和任务宕机脚本会短暂影响本机服务，只在本机验收窗口运行：`check-redis-outage.py`、`check-dispatch-crash.py`。双实例演练先启动隔离端口实例再执行；主实例和临时实例PID必须来自当次启动。

Search与Gateway构建通过、中文文件UTF-8与格式检查通过。真实学员前端完成登录、Spring关键词搜索、课程详情和有效选课159播放回归：视频readyState=4、无错误，点击播放后paused=false且播放时间推进。等待用户验收后再推进其他批次。
