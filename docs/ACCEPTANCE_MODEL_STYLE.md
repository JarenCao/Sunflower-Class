# 代码风格统一验收单

日期：2026-10-03。按用户要求，以原 Content、Media 的写法统一全项目 Java 代码。本次技术校验完成，等待用户验收确认。

## 已完成

- Model 使用普通 class、private 字段、Lombok @Data 和标准 getter/setter；保留必要的无参和全参构造器。16 个业务 record 及维护脚本中的内部 record 已统一。
- 全部 170 处 var 改为编译器核定的显式类型，范围包括业务代码、测试与维护脚本；移除通配符 import，类型使用明确 import。
- Spring 托管的 Controller、Service 和消费者采用 @Autowired 字段注入；需要注入配置的初始化使用 @PostConstruct。原生资源、值对象、枚举的必要构造器及 @Bean 参数保留。
- Auth、Learning、Orders 接入项目现有 MyBatis-Plus 依赖及 Mapper 扫描；89 处 JDBC 调用迁入业务 Mapper。Content、Media 扩展原 Mapper，不增加通用 SQL 包装层。
- 动态查询、分页与筛选使用绑定参数；保留 FOR UPDATE、唯一性约束、原子状态更新、支付去重及事务边界。MyBatis 查询保留 Map 空字段，PO 沿用下划线到驼峰映射。
- 移除三处 Controller 对 Mapper 的直接访问：发布快照、发布消息及媒资访问校验复用现有 Service；三层职责保持 API → Service → Mapper/Model。
- 补齐 DTO 的中文 @Schema 及 REST 接口的 @Tag、@Operation，添加中文业务说明；Orders 已纳入原 npm 格式工具。
- AGENTS.md 记录统一写法。所有中文源码、配置和文档使用 UTF-8。

## 技术校验

| 检查                                                                       | 结果                            |
| -------------------------------------------------------------------------- | ------------------------------- |
| parent、base、auth、content、media、search、learning、orders、gateway 构建 | 9 个入口全部通过                |
| 自动测试                                                                   | 22 项通过，失败/错误/跳过均为 0 |
| Learning 实际 MySQL 事务测试                                               | 3 项通过；专用测试数据事务回滚  |
| 原 Model JSON、构造器、getter/setter 及密码日志脱敏                        | 16 个原协议保持一致             |
| 真实 Vite 前端代理 → Gateway → 微服务                                      | 34 项通过                       |
| 五个实际数据库的绑定 SQL 与动态分支 EXPLAIN                                | 190 个分支通过，只读检查        |
| 全项目格式、UTF-8、乱码与 diff 空白检查                                    | 通过                            |
| record、var、通配符 import、生产 JdbcTemplate、Controller Mapper 访问扫描  | 均无残留                        |

构建在系统临时目录执行，避免编辑器 Java 编译器覆盖 Maven 产物；已将通过验证的产物恢复并启动七个实际服务。维护脚本只编译验证，未执行状态码迁移。

真实接口检查覆盖原学生/机构/平台账号登录、管理员权限、教师分页、机构申请筛选、选课资格和分页、目录、视频 Range 分段播放、订单列表与详情、未支付播放拒绝、平台与学生权限隔离、发布消息和快照、媒资转码状态、MinIO 对象校验、资源引用及公开封面访问。

## 人工验收

1. 查看 Auth/Learning/Orders 的 Model 和 Service，确认普通类、明确 import、显式类型和字段注入符合原写法。
2. 查看新增 Mapper 与原 Content/Media Mapper，确认数据库查询集中在 Mapper；Controller 不直接访问数据库。
3. 打开机构端 http://localhost:5173 与学员端 http://localhost:5174，复核原账号登录、课程、媒资及学员播放流程。

协议中的原 primitive 字段和 IdentityDto.companyId 类型保留，避免改变布尔默认值及“无机构时为空字符串”的原接口约定。基础设施所需原生 JDBC 连接锁和测试数据准备仍按其用途保留，不属于 Service 中的业务 SQL。

本验收只确认代码风格与兼容性。P2 真实支付宝沙箱交易与异步回调仍等待既有外部配置，未以模拟支付替代，也未标记 P2 全部完成。
