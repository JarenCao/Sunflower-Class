# 数据库归属与迁移

2026-10-01 按用户要求整理 class。复用现有服务和 MySQL 原生 RENAME TABLE，业务代码及 API → Service → Model 三层结构无需修改，没有新增依赖。

## 当前归属

| 数据库                 | 表及用途                                                                                                                                                                                                    |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| class                  | 12张课程及消息表：course_base、course_category、course_market、course_audit、course_publish、course_publish_pre、course_teacher、teachplan、teachplan_media、teachplan_work、mq_message、mq_message_history |
| learning               | 正在使用的 learning_course、course_enrollment；此前导入的 choose_course、course_tables、learn_record 三张历史结构表保留，但当前服务不读取                                                                   |
| class_archive_20261001 | auth_user（2条）、search_course（4条），原表完整归档，不删除历史数据                                                                                                                                        |
| users                  | Auth读取现有用户、角色及机构关联                                                                                                                                                                            |
| orders                 | 已导入的订单历史五表，订单微服务仍按后续清单接入                                                                                                                                                            |

Search 继续使用 Elasticsearch；Media 的数据库和文件存储配置不受影响。mq_message 是课程发布/下架事务消息，属于 Content；mq_message_history 保存课程消息历史，继续留在 class。

## 已执行步骤

1. 读代码和实际表，确认四表迁移目标无冲突、没有影响跨库移动的触发器或外键。
2. 暂停 Content 和 Learning，保存四表 UTF-8 原结构及完整行备份、逐表记录数/校验值和原 Nacos 配置。备份位于忽略目录 .runtime/db-separation-20261001，包含私有配置和历史账号散列，不提交或展示。
3. 当时通过 MySQL 原生表移动，将 learning_course/course_enrollment 移到 learning，auth_user/search_course 移到归档库。历史脚本已移除，本节仅记录已执行操作。
4. 比较全部原 class 表和目标表的记录数及 CHECKSUM TABLE EXTENDED；此前 learning 的历史三表也核对一致。
5. Nacos dev / sunflower_class 的 learning-service-dev.yaml 只把 JDBC URL 的 /class 改成 /learning，连接参数和凭据保留；重新启动 Content 和 Learning。
6. 实际验证 Gateway、两端前端代理、Auth、Content、Learning、Media、Elasticsearch 和 RabbitMQ。目录、免费/待支付选课、资格和转码视频地址仍由原接口提供。

迁移时逐表校验全部一致；之后重复事件回放会正常更新时间回执，这属于协议行为，不是迁移改写历史快照。课程159的事件42和旧下架事件39重放后，学习副本仍为42/30502，价格和资格有效期不改变。

## 初始化与历史回滚说明

新部署使用 [六库初始化](sql/INIT_DATABASES.sql)。历史增量迁移脚本已移除；已有 class 学习表的环境需制定数据迁移方案，不能创建空表替代历史副本或选课记录。旧 choose_course/course_tables 记录不会自动合并为新资格。

当时的回滚方案是暂停 Content/Learning，核查目标表名后将原表移回 class，并恢复原 Nacos 配置，再启动服务复验。该方案依赖当时保存的结构及配置，仓库不提供回滚脚本，也不保证历史本机备份仍可用；已有环境应依据实际数据制定回滚方案。

验收见 [数据库归属验收单](ACCEPTANCE_DATABASE_OWNERSHIP.md)。

## P2 更新（2026-10-02）

当前业务接入为 Auth → users、Content → class、Media → media、Learning → learning、Orders → orders；字典在 system，Search 使用 Elasticsearch 与 Redis。class 保持 12 张课程及发布消息表，未重新混入订单或学习表。

users 新增 institution_application；orders 原订单、商品和支付记录已由独立 Orders 服务读取，新增 payment_event 用于可靠支付通知；learning 新增 payment_processed 用于资格开通去重。当前六库共 45 张表。原 orders 的 8 条订单、8 条商品、22 条支付记录逐字段核对未改写。学习库的三个旧表保留，归档库 class_archive_20261001 未删除或改写。

新部署使用 [六库初始化](sql/INIT_DATABASES.sql)，仓库不再提供历史增量迁移脚本；不要用空库初始化覆盖已有业务数据。支付是否完成必须由真实平台验签或查询确定，导入历史状态不会自动赋予学员资格。
