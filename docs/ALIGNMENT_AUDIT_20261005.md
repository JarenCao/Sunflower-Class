# 前后端与数据库对齐检查汇总

检查日期：2026-10-05（北京时间）。检查对象：当前工作目录源码、两端前端、六库初始化 SQL、增量迁移、Nacos 配置模板、网关与现有测试。

## 结论

**不能判定全部对齐。主要接口路径、核心 DTO 字段和业务状态编码在源码层面对应，但存在明确的字段约束、时区和旧映射差异；实际数据库及运行数据尚未核验。**

本次完成检查、构建及报告整理，没有修改业务代码、数据库或 Nacos 配置。构建生成了本地 target/dist 与 Maven 安装产物。报告中的“源码对应”不代表当前部署配置、数据库结构、存量数据或完整业务流程已通过。

## 本次验证结果

| 检查                      | 结果                                                                           | 边界                                                    |
| ------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------- |
| JDK 21 离线 Maven install | parent、base、auth、content、media、search、learning、orders、gateway 全部成功 | 不能证明运行时基础设施可用                              |
| 两端 vue-tsc --noEmit     | 全部通过                                                                       | TS 类型不能约束实际 HTTP JSON                           |
| 两端 npm run build        | 全部通过                                                                       | 管理端有大于 500 kB 的打包提示，不影响构建成功          |
| 现有后端测试              | Auth 3 项、Orders 16 项通过；失败与错误均为 0                                  | Learning 3 项数据库测试全部跳过；其余模块本次无测试报告 |
| scripts/check-utf8.py     | 通过                                                                           | 检查源码与文档编码                                      |
| 初始化脚本表结构盘点      | 六库 45 张表                                                                   | 不是实际数据库表数                                      |
| 持久化对象字段比较        | Content 10 个、Media 5 个、Learning 1 个 PO 的持久化字段均能对应初始化列       | 不包含数据库运行时类型转换与存量数据                    |
| Mapper XML                | 阅读并检查 13 个文件，发现旧映射                                               | 生成列片段未被引用时，不等于当前接口一定报错            |
| 状态编码                  | BusinessCodes 的 31 个编码及两端直接使用的五位编码均存在于初始化字典文本       | 实际 system.dictionary 未读取                           |
| 本机接口访问              | localhost:63010、5173、5174、8848 均返回连接拒绝                               | 经沙箱外只读重试确认；无法访问实际 Gateway/前端/Nacos   |

数据库 3306、RabbitMQ 5672、Redis 6379、ES 9200 及七个业务服务端口在当前进程的本机探测均不可达；实际数据库没有取得连接，未执行 SHOW CREATE TABLE、数据抽样或 EXPLAIN。历史 docs/ACCEPTANCE_* 和 .runtime 结果只用作背景，不计入本次通过项。

## 服务与数据归属

| 前端请求前缀  | 网关/服务前缀 | 服务端口 | 数据归属             | 源码检查                                         |
| ------------- | ------------- | -------- | -------------------- | ------------------------------------------------ |
| /api/auth     | /auth         | 63080    | users                | Controller 自带 /auth；Auth 配置未额外叠加该前缀 |
| /api/content  | /content      | 63040    | class                | context-path 与网关前缀对应                      |
| /api/media    | /media        | 63050    | media、私有 S3 对象  | context-path 与网关前缀对应                      |
| /api/search   | /search       | 63060    | Elasticsearch、Redis | 没有新增 MySQL 搜索业务表                        |
| /api/learning | /learning     | 63070    | learning             | 目录副本、选课与支付去重归此库                   |
| /api/orders   | /orders       | 63090    | orders               | 订单与支付事件归此库                             |

两端 Vite 开发代理去掉 /api 后转发到 63010；Nacos 模板包含上述服务路由。生产部署也需要提供等价 /api 反向代理，Vite 的 server.proxy 不会随静态 dist 自动部署。gateway-dev.yaml 仍有 system-api 路由，但当前仓库没有 system 服务，两端未调用该路由；该路由的运行可用性不能由本仓库保证。

### 初始化表清单

- class（12）：course_audit、course_base、course_category、course_market、course_publish、course_publish_pre、course_teacher、mq_message、mq_message_history、teachplan、teachplan_media、teachplan_work。
- media（5）：media_files、media_process、media_process_history、mq_message、mq_message_history。
- users（15）：company、company_user、institution_application、menu、oauth_access_token、oauth_approvals、oauth_client_details、oauth_client_token、oauth_code、oauth_refresh_token、permission、role、teacher、user、user_role。
- learning（6）：choose_course、course_enrollment、course_tables、learn_record、learning_course、payment_processed。当前主流程使用后三类中的 course_enrollment、learning_course、payment_processed，旧三表保留不代表旧学习记录已自动迁入新资格。
- orders（6）：mq_message、mq_message_history、orders、orders_goods、pay_record、payment_event。
- system（1）：dictionary。

class_archive_20261001 是历史迁移归档库，不属于全新初始化的六库 45 表。

## 主要前后端契约对应

以下路径均为去掉 /api 后的外部路径；对应关系已阅读前端调用、Controller 与相关模型。

| 业务       | 前端请求与后端路径                                                                                                                             | 字段/响应检查                                                                       |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| 登录身份   | GET /auth/csrf、/auth/me；POST /auth/login、/auth/register、/auth/logout                                                                       | Cookie 身份、CSRF 请求头；id 保留字符串，role/companyId 对应                        |
| 老师账号   | GET/POST /auth/teachers                                                                                                                        | 机构范围由后端决定；canManageTeachers 与管理入口对应                                |
| 机构入驻   | GET/POST /auth/institution-applications；GET /auth/platform/institution-applications；POST /auth/platform/institution-applications/{id}/review | 申请输入、审核 approved/reason、列表 camelCase 别名对应                             |
| 课程管理   | POST /content/course/list；GET/DELETE /content/course/{id}；POST/PUT /content/course                                                           | pageNo/pageSize 与请求体筛选对应；CourseBaseInfoDto 合并营销字段                    |
| 分类树     | GET /content/category/node                                                                                                                     | childrenTreeNodes 在前端转换为 children                                             |
| 章节小节   | GET /content/techplan/{id}/tree-nodes；POST /content/teachplan；DELETE /content/teachplan/{id}；PUT /content/teachplan/{id}/move               | techplan 拼写与前端一致；parentId 输入映射 parentid；两级目录和 isPreview 对应      |
| 课程讲师   | GET/POST /content/course/{courseId}/teachers；PUT/DELETE 对应 teacherId 子路径                                                                 | teacherName/position/introduction/photograph 对应；与登录老师账号不同业务           |
| 审核发布   | commit/review/history/queue/detail 与 /content/courseaudit 下路径；POST /content/coursepublish/{id}；PUT offline                               | 业务码 RestResponse 与前端 check() 对应；history SQL 有显式字段别名                 |
| 发布消息   | GET /content/publication-messages；POST /{id}/retry                                                                                            | 机构与 courseId 约束；阶段状态、执行次数和失败字段对应                              |
| 普通媒资   | POST /media/files、/media/upload/coursefile；GET content/process/references；POST retry；DELETE files/{id}                                     | filedata、分页、删除 202/204 与前端处理对应                                         |
| 分片上传   | POST /media/upload/checkfile、checkchunk、uploadchunk、mergechunks                                                                             | fileMd5、chunk、file、fileName、chunkTotal 与绑定名对应；RestResponse 业务码已检查  |
| 课程发现   | GET /search/courses、/search/courses/{id}、/search/categories                                                                                  | 搜索结果取正式快照；name→title、分类名、等级、价格、teachers JSON 转换对应          |
| 课程目录   | GET /learning/courses/{id}/directory                                                                                                           | teachplan JSON 在两端模型中保持一致；与搜索详情并行读取                             |
| 选课学习   | GET/POST /learning/enrollments/{id}；GET /learning/enrollments；POST /{id}/renew                                                               | enrollmentType/status/qualification/courseAvailable/renewable 对应                  |
| 播放与试学 | GET /media/playback/{courseId}/{lessonId}、/media/trial/{courseId}/{lessonId}                                                                  | url/expiresAt 对应；后端向 Learning 验证资格和正式目录                              |
| 订单支付   | GET/POST /orders/purchases 及 id/pay/refresh 子路径                                                                                            | 订单 id 为字符串；price/status/learningActivated 与前端对应；二维码响应 qrCode 对应 |

分页保持 items/count/page/pageSize；普通 DTO、RestResponse 和无正文响应是现有接口的不同约定，前端已分别处理，不能一律加 result 包装。日期格式的显示语义仍有下面列出的差异。

## 明确差异与处理建议

### 1. P1：标签在保存和提审之间长度不一致

证据：docs/sql/INIT_DATABASES.sql 的 course_base.tags 为 varchar(50)，course_publish_pre.tags、course_publish.tags 为 varchar(32)。frontend-admin/src/views/CourseEditorView.vue 的标签输入无 maxlength，AddCourseDto 也没有长度限制。

影响：33～50 字符标签能进入草稿表，在创建提审快照时可能因字段超长失败；非严格 SQL 模式还可能发生截断。

建议：按现有草稿容量将三表标签统一为 50，并使后端与前端约束一致。现有库需要单独 ALTER 迁移；仅修改 INIT_DATABASES.sql 不会升级旧表。本次没有执行迁移。

### 2. P1：课程输入校验与下游数据库约束不完整

证据：AddCourseDto 仅名称有 NotBlank；CourseBaseInfoServiceImpl.validateCharge() 主要校验收费方式和收费价格大于零。名称 varchar(100)、人群 varchar(500)、封面 varchar(500)、教学计划名 varchar(64)，相应表单/DTO 未完整限制长度。课程价格输入没有两位精度或金额上限约束；course_market 为 decimal(10,2)。Content 未统一拒绝负 validDays，Learning 选课及续期则检查负数，course_enrollment 有非负 CHECK。

影响：超长数据落库失败；超精度价格可能被数据库舍入；负有效天数能在课程侧进入配置，却在选课侧被拒绝；极大有效期可能超出数据库日期范围。免费价格为空或负数时，Content 也没有完整归零，学员展示与 Learning 免费选课又按零价处理。

建议：复用现有 Bean Validation 和 validateCharge 校验字段长度、DECIMAL 容量/精度、非负有效期与合理日期范围；前端输入同步提示和限制。先确定免费价格规范，再在服务端执行，不新增校验框架。

### 3. P1：学习时间与订单时间展示语义不同

证据：DEPLOYMENT.md 规定 Learning/Orders 存 UTC。OrderServiceImpl.displayTime() 显式 UTC→Asia/Shanghai；LearningCourseServiceImpl.record() 直接返回 CourseEnrollment 的 LocalDateTime；MyCoursesView.vue 只 replace('T',' ')。

影响：采用文档规定的 UTC 连接配置时，“我的学习”会显示 UTC 墙上时间，订单页面显示北京时间，存在 8 小时显示差。Jackson 默认时区配置不会把没有时区的 LocalDateTime 自动从 UTC 转为北京时间。

建议：统一 API 时间契约，复用现有明确转换或返回带时区时间并由前端格式化。数据库资格判断继续使用数据库时间，不因显示修正而改写历史时间。实际运行配置未读取，当前部署是否已发生该问题尚待核验。

### 4. P2：Mapper XML 存在旧字段和不完整的生成映射

证据：MediaFilesMapper.xml 的 BaseResultMap 和 Base_Column_List 含 timelength，但 MediaFiles 与 media.media_files 都没有该字段；列片段缺少 file_path、file_size、delete_error。CourseBaseMapper.xml 映射 createPeople/changePeople，但 CourseBase 没有这两个 Java 属性。部分 MediaProcess/MQ XML 生成列片段也没有覆盖新增重试字段及阶段字段。

影响：这些模板与现有模型/初始化结构不一致；若被显式引用可能产生未知列错误或读取不完整。目前主要 CRUD 复用 MyBatis-Plus，不能仅凭旧模板宣称当前接口已经报错。

建议：仅修正现有映射及列片段，不向数据库添加没有当前用途的 timelength 字段。保留 TeachplanMapper 的 one_/two_ 查询别名，它们有真实 SELECT 别名，属于正确映射。

### 5. P2：前端必填类型未反映后端可省略的空值

证据：admin/types.ts 的 Course.tags、originalPrice 等为必填，SQL 允许空，AddCourseDto 也允许空；JacksonConfig 忽略 null。学员端 CourseEnrollment 已用可选字段兼容未选课响应。部分入驻与媒资接口仍以 any 或未明确类型接收响应。

影响：前端编译通过不代表所有历史课程响应都符合声明；接口可返回缺少字段的对象。不是已证实的所有页面崩溃。

建议：对实际可为空字段声明可选或在已有适配层规范化；涉及原始输入/响应的约束通过后端保证。无需创建新的统一响应层。

### 6. P2：文档与当前功能存在残留冲突

证据：STATUS_CODES.md 仍写“审核员通过/驳回接口属于下一开发项”和开发期 sunflower.company-id；实际已有审核 Controller 和 JWT 机构身份。DEVELOPMENT_CHECKLIST.md 开头写“课程预览不在本项目范围”，但现在有专门匿名试学入口；data.ts 注释仍说讲师信息未提供，实际已解析 teachers。

建议：把课程页面预览与匿名小节试学分清，删除已过期的待开发/身份描述；以当前 Controller、权限规则和部署步骤为准。DATABASE_OWNERSHIP.md 早期订单未接入说法应明确为历史记录，以其 P2 更新为当前状态。

## 兼容风险和待实际验证项

- users.user.id 为 varchar(64)，company_user.user_id、teacher.user_id 为 varchar(32)。当前开户生成 32 字符 UUID，当前流程兼容；若现有用户使用 33～64 字符 ID，相关关系表容量不兼容。需要只读检查实际 ID 长度后决定迁移，不能直接假定有坏数据。
- users.company.id 是 varchar(32)，Content/Media.company_id 是 bigint，前端是 number。当前机构创建使用正 long 的十进制字符串，服务端沿用此规则；旧非数字机构 ID 或超范围 ID 需抽查。前端应避免把大机构 ID 当作精确数字参与计算/请求归属。
- Search 与 Learning 是异步正式快照副本。前端详情同时取两服务，但不比较 eventId；恢复期间可能暂时读到不同版本。需要比较 Content 最新事件、ES external version、learning_course.event_id 及回执，不能把异步副本“字段一致”当作“数据已同步”。
- 实际 Nacos dataId/group/namespace、路由、数据库 URL、UTC 参数、凭据、索引和私有桶无法读取。AUTH_DB_URL/LEARNING_DB_URL 允许外部覆盖，模板指向正确库不保证运行实例未指错库。
- INIT_DATABASES.sql 使用 CREATE IF NOT EXISTS，不升级旧结构；增量脚本中含一次性 ALTER 和 RENAME，不可为了检查全部重跑。应逐表对照现库字段、索引、CHECK、默认值和迁移历史。
- 真实发布→MQ→ES/Learning、支付确认→payment_event→payment_processed→资格→回执、转码与受保护播放，本次没有执行写入或端到端验收。
- 学员展示的学习人数、课程总时长仍是明确占位；不是数据库字段丢失的证据。直播/退款/订单完成状态出现在选择项或字典，也不代表完整直播、退款、完成业务已实现。

## 建议收尾顺序

1. 先修正标签容量及课程输入约束，补时间展示一致性；直接扩展现有 DTO/服务/表单。
2. 整理旧 Mapper XML 和过期文档，按可空语义调整现有 TS 类型。
3. 基础设施可访问后，只读核对六库结构、Nacos 实际配置、历史 ID、状态字典、孤儿关系与事件版本。
4. 在隔离环境验证长标签、超精度金额、负/超大有效期、UTC 时间、重复发布/支付与失败恢复；运行 Learning 3 项真实数据库测试并确认不跳过。
5. 汇总真实执行证据后再给“全部对齐”的验收结论。

本次交付是对齐检查汇总；上述业务修正和数据库迁移尚未实施，未把建议项计为已修复。
