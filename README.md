# Sunflower Class · 小葵花课堂

基于 Java 21、Spring Boot / Spring Cloud 和 Vue 3 的在线课程平台，覆盖课程制作、审核发布、搜索、选课、视频学习与订单支付，提供老师与平台管理员工作台、学员门户两个前端。

## 核心功能

| 角色       | 主要功能                                                                                 |
| ---------- | ---------------------------------------------------------------------------------------- |
| 学员       | 注册登录、搜索课程、匿名试学、免费选课与续期、查看学习记录和订单、申请成为老师           |
| 老师       | 管理本人教学空间的课程、章节和讲师介绍，上传媒资、查看转码状态，提交审核、发布与下架课程 |
| 平台管理员 | 审核课程和老师申请，创建与查询老师账号                                                   |

课程流程：老师上传媒资并制作课程 → 管理员审核 → 老师发布 → 消息同步 Search / Learning → 学员选课学习。收费课程通过支付宝沙箱确认交易后，由可靠支付事件开通学习资格。

老师与学员账号相互独立，老师开户时创建独立教学空间。播放前由 Learning 校验资格，Media 提供短期签名地址。业务规则详见 [项目梳理](docs/PROJECT_GUIDE.md)。

## 技术与模块

后端采用 MyBatis、MySQL、RabbitMQ、Redis、Elasticsearch、Nacos 和兼容 S3 的对象存储；视频通过 Docker / FFmpeg 转码。前端采用 Vue 3、TypeScript、Vite、Pinia 和 Axios，管理端使用 Element Plus。

| 目录                           | 职责                                               | 默认端口 |
| ------------------------------ | -------------------------------------------------- | -------- |
| `parent/`、`base/`             | 依赖管理、公共响应、异常、JWT 鉴权、消息协议与工具 | —        |
| `gateway/`                     | API 入口、服务路由、鉴权与限流                     | 63010    |
| `auth/`                        | 登录注册、老师申请与账号管理                       | 63080    |
| `content/`                     | 课程、目录、审核、发布与同步事件                   | 63040    |
| `media/`                       | 图片与视频上传、转码、媒资管理与播放地址           | 63050    |
| `search/`                      | 已发布课程搜索与缓存                               | 63060    |
| `learning/`                    | 课程目录副本、选课与学习资格                       | 63070    |
| `orders/`                      | 订单、支付宝沙箱交易与支付事件                     | 63090    |
| `frontend-admin/`              | 老师与平台管理员工作台                             | 5173     |
| `frontend-student/`            | 学员门户                                           | 5174     |
| `frontend-shared/`             | 两个前端共用的错误提示                             | —        |
| `docs/`、`deploy/`、`scripts/` | 项目文档、部署配置与检查脚本                       | —        |

业务服务按 `*_api`、`*_service`、`*_model` 分层。Java 源码位于 `src/main/java`，配置与 Mapper XML 位于 `src/main/resources`，测试位于 `src/test/java`。

## 本地运行

### 1. 准备环境与配置

- 安装 JDK 21、Maven、Node.js 20.19+ 或 22.12+。
- 配置 MySQL、Nacos、RabbitMQ、Redis、Elasticsearch 和私有对象存储。当前转码部署使用 Windows Java 进程与 WSL 内 Docker / FFmpeg。
- 新环境使用 [数据库初始化脚本](docs/sql/INIT_DATABASES.sql)，再按 [部署说明](docs/DEPLOYMENT.md) 配置 Nacos、服务进程环境变量和首个平台管理员。

初始化脚本只提供表结构及基础字典、分类、角色，不提供默认账号或密码。凭据放在外部配置或被忽略的 `.runtime/` 目录中。

### 2. 构建后端

在仓库根目录执行，先安装 `parent` 和 `base`，再构建业务服务。仓库没有根聚合 POM 或 Maven Wrapper。

```sh
mvn -f parent/pom.xml install
mvn -f base/pom.xml install
mvn -f auth/pom.xml install
mvn -f content/pom.xml install
mvn -f media/pom.xml install
mvn -f search/pom.xml install
mvn -f learning/pom.xml install
mvn -f orders/pom.xml install
mvn -f gateway/pom.xml verify
```

### 3. 启动服务与前端

在独立终端启动各服务，例如：

```sh
mvn -f auth/auth_api/pom.xml spring-boot:run
mvn -f content/content_api/pom.xml spring-boot:run
mvn -f gateway/pom.xml spring-boot:run
```

Media、Search、Learning、Orders 使用各自 `*_api/pom.xml` 执行相同命令；完整业务流程需要启动对应服务。

安装两端依赖：

```sh
npm --prefix frontend-admin ci
npm --prefix frontend-student ci
```

然后在两个独立终端分别执行：

```sh
npm --prefix frontend-admin run dev
npm --prefix frontend-student run dev
```

访问 [管理端](http://localhost:5173) 或 [学员端](http://localhost:5174)。两端通过 Gateway 访问真实 API；可在各自 `.env.local` 中设置 `VITE_GATEWAY_TARGET`，默认值为 `http://localhost:63010`。配置细节见 [管理端说明](frontend-admin/README.md)、[学员端说明](frontend-student/README.md) 和 [真实联调说明](docs/LIVE_INTEGRATION.md)。

## 开发与校验

Java 使用四空格缩进，Vue、TypeScript 和 CSS 使用两空格；关键业务规则使用中文注释，文本保存为 UTF-8。贡献规范见 [AGENTS.md](AGENTS.md)。

根目录 npm 依赖用于 Prettier 和 Java 格式化插件，前端依赖仍需分别安装：

```sh
npm ci
npm run format
npm run format:check
python scripts/check-utf8.py
```

`format` 写入格式调整，`format:check` 仅检查。按改动范围执行后端测试和前端类型检查、生产构建：

```sh
mvn -f content/content_service/pom.xml test
mvn -f gateway/pom.xml test
npm --prefix frontend-admin run build
npm --prefix frontend-student run build
```

集成测试需要相应基础设施；构建通过不能替代真实业务验收。

## 文档导航

媒资重构方案见 [媒资管理统一流程](docs/MEDIA_MANAGEMENT_FLOW.md)，沿用 Spring 定时任务与 RabbitMQ，不引入 XXL-Job。

课程发布重构目标见 [课程发布流程](docs/COURSE_PUBLICATION_FLOW.md)，其中列明教程流程与当前实现的差异。

重构前先阅读 [重构前期准备与边界](docs/REFACTOR_PREPARATION.md)，确认功能范围、数据归属和数据库清理候选。

| 文档                                                                         | 内容                                    |
| ---------------------------------------------------------------------------- | --------------------------------------- |
| [项目梳理](docs/PROJECT_GUIDE.md)                                            | 模块职责、三角色权限、业务流程与初始化  |
| [需求文档](docs/REQUIREMENTS.md) / [开发清单](docs/DEVELOPMENT_CHECKLIST.md) | 产品需求与实现清单                      |
| [部署说明](docs/DEPLOYMENT.md)                                               | 基础设施、数据库、Nacos、启动与支付配置 |
| [认证说明](docs/AUTHENTICATION.md)                                           | 认证服务与身份校验                      |
| [课程消息服务](docs/COURSE_MESSAGE_SERVICES.md)                              | 发布消息与 Search / Learning 同步       |
| [课程选课](docs/COURSE_ENROLLMENT.md) / [课程播放](docs/COURSE_PLAYBACK.md)  | 学习资格与受保护播放                    |
| [支付验收](docs/ACCEPTANCE_ORDERS_PAYMENT.md)                                | 订单、沙箱支付与学习开通验收            |
| [状态编码](docs/STATUS_CODES.md)                                             | 五位业务状态码与迁移                    |
| [前端技术方案](docs/FRONTEND_TECH_PROPOSAL.md)                               | 前端架构与实现方案                      |

两个前端不使用浏览器本地存储或演示数据；空库没有课程是正常状态。支付宝真实沙箱付款与公网异步回调仍保留未验收状态，生成二维码不代表付款成功。历史验收文档中的账号和业务记录不代表当前环境的数据。
