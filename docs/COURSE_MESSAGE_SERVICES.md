# 课程消息与下游服务

## 本次范围

新增独立 `search_api`（63060）和 `learning_api`（63070），注册到 Nacos。目录风格与 Content、Media 一致：聚合模块下分别放置 `*_model`、`*_service`、`*_api`。搜索通过 Spring 原生 RestClient 访问已有 Docker Elasticsearch 9.4.2，学习服务访问独立 MySQL 表 `learning_course`；两端通过 RabbitMQ 同步正式快照，不查询草稿。学习服务已扩展选课、学习资格及播放授权核查，详见 [选课规则](COURSE_ENROLLMENT.md) 和 [播放说明](COURSE_PLAYBACK.md)。

## 配置与部署

当前 Content 使用 `class`，Learning 使用独立 `learning` 库；2026-10-01 已将学习目录与选课表移出课程库。Nacos 分组仍为 `sunflower_class`，见 [数据库归属说明](DATABASE_OWNERSHIP.md)。

1. 新部署执行 [六库初始化](sql/INIT_DATABASES.sql)，课程消息表位于 class，学习目录表位于 learning。历史增量迁移脚本已移除；已有环境需核查表结构与数据归属，不能另建空表替代原副本。
2. 向 Nacos 的 `dev / sunflower_class` 上传 `nacos/search-api-dev.yaml`、`nacos/search-service-dev.yaml`、`nacos/learning-api-dev.yaml`、`nacos/learning-service-dev.yaml`。搜索配置提供 ES 地址和专用索引名；学习配置提供已有 MySQL 数据源。数据库示例里的环境占位符须替换成实际外部配置，不把密码写进仓库。两服务复用 `common/rabbitmq-dev.yaml`、日志和 Swagger 配置。
3. 把 `nacos/gateway-course-routes.yaml` 的两条路由合并到已有网关配置，保留 Content、Media 等路由。
4. 安装 parent、base、content 构件，再分别运行 `mvn -f search/pom.xml install`、`mvn -f learning/pom.xml install`。启动入口为 `mvn -f search/search_api/pom.xml spring-boot:run` 和 `mvn -f learning/learning_api/pom.xml spring-boot:run`。仍需启动 Content、Media、Gateway 和两个前端。服务日志资源复用现有 `logback.xml` 风格。
5. 新环境通过正常审核发布流程生成课程事件。历史课程事件补齐脚本已移除；已有课程若需补齐事件，应核查持久化快照及现有事件，避免重复生成。旧 `payload IS NULL` 消息保留但不投递，不能用后来的快照伪造旧事件内容。

## 当前实现的事件与状态

本节描述重构前的现有字段与完成条件：Redis 独立阶段尚未落实。重构目标以 [课程发布统一流程](COURSE_PUBLICATION_FLOW.md) 为准，三个业务阶段全部成功后归档，不能继续沿用两端成功即完成的判断。

发布和下架的正式快照与 `course_publish` 消息在同一事务提交，事件内容保存在 `payload`，不随后续编辑变化。自增消息 ID 是版本号。学习目录以课程主键做原子更新；ES 使用 `version_type=external`，相同或旧版本产生 409 版本冲突，消费者确认后忽略，不覆盖已有文档。下架保留状态为 `30503` 的版本墓碑，避免旧消息重新上架课程。消费成功也会回执。

搜索服务首次访问时创建 `sunflower_courses` 专用索引，不使用或修改其他已有索引。标题与标签用 `wildcard` 字段实现包含匹配，分类和发布状态用 `keyword`，原始快照关闭字段索引但保留 `_source`。单节点配置一主分片、零副本；写入使用 `refresh=wait_for`，回执成功后搜索可见。当前分页最多访问 ES 默认的一万条结果窗口，超出返回 400 并提示缩小查询范围。

| 字段          | 本次含义                                             |
| ------------- | ---------------------------------------------------- |
| `state`       | 0 待完成，1 两端同步完成，2 发生失败或达到投递上限   |
| `stageState1` | 搜索与学习队列投递确认                               |
| `stageState2` | Redis 尚未实现，保持 0，不参与本次完成判断           |
| `stageState3` | Elasticsearch 索引消费结果                           |
| `stageState4` | 学习目录消费结果；本次明确替代原未使用的订单同步占位 |

内容服务每五秒扫描，原子抢占一分钟租约，最多投递十次。投递须同时满足 publisher confirm 成功、没有 mandatory return；连接失败、确认超时、无路由均记录失败。未收到回执会重投，进程退出后租约到期可恢复。网络故障时界面暂时不同步，属于最终一致性。

消费者先完成 ES 写入或学习目录事务，再可靠发送回执，最后确认原消息。失败进入十秒 TTL 重试队列；十次后进入对应 `course.*.failed.queue` 保留原事件。回执失败也会重试，不把写入误判为整条流程完成。机构端“同步状态”显示最近十条事件，可恢复未完成事件；失败队列里的旧副本消息供排查，手动恢复重新投递原持久化事件。

## 实际接口

- `GET /search/courses?pageNo=1&pageSize=10&q=nacos&category=`：标题/标签搜索，统一 NFKC 与大小写，转义 ES 的 `*`、`?` 通配符，返回真实分页总数。
- `GET /search/categories`、`GET /search/courses/{id}`：分类和发布详情。
- `GET /learning/courses/{id}/directory`：已发布目录，下架或不存在返回 404。
- `GET /content/publication-messages?courseId=...`、`POST /content/publication-messages/{id}/retry`：本机构同步状态及恢复。教学空间来自已验签身份，服务端校验角色与课程归属。

学员首页读取搜索服务，全部课程由服务端分页（每页十条）；详情从搜索服务和学习服务分别获取信息与目录。机构发布成功不等于下游已同步，界面提供明确提示和刷新入口，无浏览器持久化或演示回退。

## 重构已确认的搜索同步与缓存规则

本节为用户确认的重构目标，需结合现有实现验证。

- ES 仅保存正式发布课程的搜索投影，不读取草稿。发布、重新发布及下架通过持久化事件同步，按事件版本拒绝旧数据覆盖。
- 下架保留版本标记，查询过滤下架课程，防止迟到发布消息恢复可见性。
- Search 保留独立查询缓存，事件更新 ES 前后推进缓存版本，使旧缓存失效。
- Redis 查询故障可回源 ES；ES 故障明确返回错误，不伪造空列表。
- ES 更新和必要缓存失效成功后才发送成功回执，失败由发布任务重试。队列发送确认不代表搜索同步完成。
- 提供从正式发布数据重建索引的维护流程，保留事件版本并核对下架数据；具体重建、并发增量同步及切换步骤在实施时设计。
- 第一轮保留当前搜索功能，不新增推荐、复杂排序或新的搜索组件。
