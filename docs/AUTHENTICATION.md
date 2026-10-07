# 独立认证服务部署

认证入口位于 `auth/auth_api/`，服务名 `auth-api`、端口 63080，读取 `users` 数据库。Content 不再提供登录接口。认证服务复用已有 Spring Security、BCrypt、JWT、HttpOnly Cookie 和 CSRF 实现，不新增另一套认证框架。

## 数据与身份

- 账号、密码散列、显示名和启用状态来自 `users.user` 的 `username/password/name/status`。仅 `status='1'` 可新登录；原有微信标识等非 BCrypt 字段不能用于密码登录，不提供明文密码回退。
- `utype=10201/10202/10203` 分别对应 `student/teacher/admin`，与 `system.dictionary` 的 102 用户类型字典一致。普通身份以用户类型为准，旧 `user_role` 中的 admin/super 关联不会给学生提权。管理员类型须关联启用的 `admin`、`super` 或旧 `reviewer` 角色，统一签发平台管理员身份；原课程审核员已并入平台管理员。
- 机构编号优先读取用户表；用户表为空时读取 `company_user` 的唯一机构关联。多机构关联或两处机构编号冲突时拒绝登录。学员、管理员不因旧机构字段获得机构身份。
- 新环境通过 [六库初始化](sql/INIT_DATABASES.sql) 创建登录锁定字段；历史增量迁移脚本已移除，已有环境需核查实际表结构。五次失败锁定十五分钟，成功登录清空计数。
- 当前认证服务只读取 `users.user`。业务数据已清空时，不应依赖历史验收账号或运行目录中的旧凭据；按 [项目梳理](PROJECT_GUIDE.md) 初始化首个管理员并重新开户。

## Nacos 与 Gateway

在 `dev` 命名空间、`sunflower_class` 分组发布 [auth-service-dev.yaml](nacos/auth-service-dev.yaml)，使用实际 `users` 数据源。真实数据库凭据只放在受保护的配置中心或环境变量，示例不包含密码。

继续使用 `common/auth-dev.yaml` 中的统一 JWT 外部密钥。所有服务使用相同密钥并检查签发者 `sunflower-class`、签名及有效期。Cookie HTTPS 环境启用 Secure，本机 HTTP 显式关闭；JWT 三十分钟，不自动续期，退出不撤销已复制的令牌。

将 [认证路由](nacos/gateway-auth-route.yaml) 合并到 `gateway-dev.yaml` 的路由列表：`/auth/** → lb://auth-api`，不改写路径。认证服务本身没有 servlet context-path。机构端和学员端均使用同源 `/api/auth`，由 Vite 去掉 `/api` 后交给 Gateway。

| Gateway 路径        | 行为                                                   |
| ------------------- | ------------------------------------------------------ |
| `GET /auth/csrf`    | 返回框架 CSRF 令牌和请求头名称                         |
| `POST /auth/login`  | 只接受用户名和密码，设置 HttpOnly、SameSite=Lax Cookie |
| `GET /auth/me`      | 读取已验签身份，不返回密码、散列或 JWT                 |
| `POST /auth/logout` | 清除登录 Cookie                                        |

所有写操作携带与 Cookie 配对的 CSRF 令牌，跨 Content、Media 的 CSRF Cookie 使用根路径。两个前端在同一主机上共享 Cookie，切换账号或退出会影响另一入口。直接访问各服务端口仍验签，不能用伪造身份头绕过认证。

## 构建与运行

先安装 parent、base，再执行 `mvn -f auth/pom.xml install`、`mvn -f auth/auth_api/pom.xml spring-boot:run`。认证服务按项目三层模块组织：`auth_model` 保存登录与身份 DTO，`auth_service` 保存服务接口、实现、数据库访问、校验和 JWT 签发，`auth_api` 保存启动入口、配置及 HTTP 控制器；依赖方向为 API → Service → Model。数据库查询复用现有 MyBatis Mapper，无新依赖。

迁移时对 Content 执行 `mvn -f content/pom.xml clean install`，避免已移除控制器的 class 文件留在增量构建目录；重新构建并启动 Gateway。源码和配置统一 UTF-8，运行时使用 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`。

管理端：`http://localhost:5173/login`；学员端：`http://localhost:5174/login`。同一浏览器保持相同主机名。当前已接入三角色、学员注册、老师申请、选课与订单以及受保护视频；微信配置和真实沙箱付款仍按原验收状态处理，见 [选课部署说明](COURSE_ENROLLMENT.md) 和 [播放说明](COURSE_PLAYBACK.md)。

## 角色授权

复用 Spring Security 原生 JWT 权限转换器，将已验签的 `role` 转成 `ROLE_` 权限。Content、Media 同时检查角色和现有机构归属，直接访问服务端口也生效。前端菜单与路由同步限制，不能用隐藏菜单代替后端授权。

| 身份    | 可用范围                                                                             |
| ------- | ------------------------------------------------------------------------------------ |
| student | 浏览、试学、本人选课及订单、有效资格下的学习、申请成为老师                           |
| teacher | 管理原所属机构的课程、教学计划、师资和媒资；提审、发布、下架；不能审核或管理老师账号 |
| admin   | 统一课程审核、老师申请审核、创建和查看老师账号；不能编辑老师课程                     |

原机构管理员按老师身份使用，旧老师和课程的机构归属保持不变。新老师自动拥有独立教学空间。已启用的旧课程审核员与平台管理员重新登录后统一使用 admin，管理员类型但没有启用的显式管理角色不能登录。

公开 POST /auth/register 固定创建学员，字段为 username、name、password、confirmPassword。POST /auth/teachers 只允许平台管理员创建老师，字段同注册；GET /auth/teachers 只允许平台管理员分页查看全部老师，响应不包含密码散列。两类开户均复用 BCrypt、账号唯一约束和事务。

老师申请复用 POST /auth/institution-applications，简化为 name、mobile、intro、username、password、confirmPassword 六项；GET 同路径只返回本人申请。平台管理员从 /auth/platform/institution-applications 查询和审核，通过后开通独立老师账号，原学员账号及记录保留。历史九字段申请仍兼容，历史审核结论不变。

角色在登录时签入 JWT，升级后须退出并重新登录，不能只刷新旧令牌。两个前端共享同一主机 Cookie，验收不同身份时先切换登录。

最新权限和开发校验见 [三角色权限与流程](ROLE_SIMPLIFICATION_PLAN.md)。此前角色验收文档记录历史实现，以本节为准。

## 重构已确认的认证与权限边界

以下为用户确认的重构规则，需对照实现验证：

- 保留 JWT、HttpOnly Cookie、CSRF 和 BCrypt，不恢复旧 OAuth 体系。
- 固定学员、老师、平台管理员三角色，微信登录和动态菜单权限暂缓。
- 学员仅访问本人学习与订单，老师仅管理本人教学空间，平台管理员负责审核及老师开户。
- 老师与学员账号独立，课程讲师介绍不作为登录身份。
- 角色、用户 ID 和教学空间从已验签身份取得，请求参数不能授予身份或资源归属。
- Gateway 执行入口鉴权，各业务服务继续校验角色、资源归属及关键账号状态。
- 统一重复权限判断，保留当前登录锁定、Cookie、退出和令牌有效期规则。
- 验证绕过网关、跨空间、跨学员和伪造身份请求均被拒绝，密码不进入响应或日志。
