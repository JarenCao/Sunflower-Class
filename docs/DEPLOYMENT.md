# 本机部署与CI交付

适用：Java 21、Maven、Node 24、Windows PowerShell、WSL Ubuntu-24.04 与 Docker。现有转码实现调用 WSL 内 Docker，Java服务在 Windows 启动。Compose负责基础设施，不声称支持把当前 Media 直接部署到 Linux 应用容器。

## 数据库

全新环境执行 [INIT_DATABASES.sql](sql/INIT_DATABASES.sql)：创建 system、users、class、media、learning、orders 的45张表，仅带公开字典、课程分类及角色定义，没有用户密码、机构、订单、选课或课程业务数据。脚本使用明确库名，可重复初始化；旧表不会自动升级。

历史增量迁移脚本已移除，仓库仅保留全新环境初始化脚本；已有数据库需根据实际结构制定升级方案，初始化脚本不会自动升级旧表。订单及支付事件归 Orders，学习资格与支付去重归 Learning，不写入 class。

首次平台管理员由部署人员在 users 中受控创建：用户名唯一、BCrypt密码散列、utype=10203、status=1，并绑定启用的 admin 角色。不能通过公开注册取得平台权限。当前业务数据已清空，不能依赖此前验收账号。学员公开注册后可申请成为老师；平台管理员审核通过后创建独立老师账号和教学空间，也可直接创建老师账号。

## 基础设施

[deploy/compose.yaml](../deploy/compose.yaml)固定镜像版本并只绑定本机端口。将口令放在运行环境或忽略目录 .runtime/deploy.env，文件不要提交。须配置 MYSQL_ROOT_PASSWORD、NACOS_AUTH_TOKEN、NACOS_AUTH_IDENTITY_KEY、NACOS_AUTH_IDENTITY_VALUE、RABBITMQ_USER、RABBITMQ_PASSWORD、REDIS_PASSWORD、MINIO_ROOT_USER、MINIO_ROOT_PASSWORD。Nacos令牌为32字节以上密钥的Base64值。

```powershell
# 已有基础设施可直接复用；在全新机器启动时执行。
docker compose --env-file .runtime/deploy.env -f deploy/compose.yaml config --quiet
docker compose --env-file .runtime/deploy.env -f deploy/compose.yaml up -d
```

在 WSL 内操作时进入对应 /mnt/d/... 项目目录再执行同样命令。Compose的MySQL入口仅在空数据卷执行初始化。已有本机服务占用同一端口时复用现有服务，不启动第二套。

MinIO建立 mediafiles 与 video 两个私有桶，不设置公开读取；通过SDK上传和签名地址访问。当前本机使用已有兼容S3对象存储，数据与凭据保留。转码按现有配置使用 linuxserver/ffmpeg:6.1.1，在 WSL 内预先拉取镜像并保证 /tmp/xiaokuihua-ffmpeg 可写。

## Nacos配置

创建ID为 dev 的命名空间，按 [docs/nacos](nacos/) 模板建立如下配置。配置内容中的环境变量在每个Java服务的进程环境解析，Compose的环境不会自动传入 Windows Java进程。

| 分组            | 配置                                                                                                            |
| --------------- | --------------------------------------------------------------------------------------------------------------- |
| common          | auth-dev.yaml、logging-dev.yaml、swagger-dev.yaml、rabbitmq-dev.yaml、redis-dev.yaml、alipay-dev.yaml（沙箱）   |
| sunflower_class | auth-service-dev.yaml；content/media/search/learning/orders 的 service-dev.yaml、api-dev.yaml；gateway-dev.yaml |

模板已补齐各服务实际import。Auth当前端口在 auth-service-dev.yaml，无须额外import auth-api-dev.yaml。Nacos自身启用认证时，启动Java前设置 NACOS_USERNAME、NACOS_PASSWORD，另设 NACOS_SERVER_ADDR、NACOS_NAMESPACE（默认localhost:8848/dev）；客户端在读取业务配置前使用这些环境变量登录。Nacos初始账户由部署人员管理，参考[官方认证说明](https://nacos.io/en/docs/v2/guide/user/auth/)。

配置 MYSQL_HOST、MYSQL_PORT、MYSQL_USER、MYSQL_PASSWORD；Auth模板使用 AUTH_DB_URL/AUTH_DB_USERNAME/AUTH_DB_PASSWORD，Learning模板使用 LEARNING_DB_URL/LEARNING_DB_USER/LEARNING_DB_PASSWORD。两者URL分别明确指向 users、learning。所有服务共享 SUNFLOWER_AUTH_SECRET（至少32字节的Base64密钥），HTTP本机联调设 COOKIE_SECURE=false，HTTPS部署为true。RabbitMQ虚拟主机与模板统一为sunflower。

订单与学习交易时间统一存UTC。Learning JDBC URL须包含 connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true，Orders模板已经包含；Windows本机原Learning时间为UTC，本轮没有改写历史时间。订单页面展示北京时间，支付宝回调北京时间在订单服务转换为UTC后校验。JVM使用 -Duser.timezone=Asia/Shanghai，兼容官方SDK的交易日期解析。参见[MySQL官方时间说明](https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html)。

Search配置 ELASTICSEARCH_ENDPOINT（或调整模板endpoint）、索引 sunflower_courses；Redis配置 REDIS_HOST/REDIS_PORT/REDIS_PASSWORD。Media配置 MINIO_ENDPOINT及访问凭据，确认Windows能访问其端点。Gateway配置包含orders-api的Nacos发现路由。

## 启动与验证

```powershell
$env:JAVA_HOME = '你的 JDK 21 目录'
# 保证 mvn 已加入 PATH，先配置前述环境与Nacos。
foreach ($module in @('parent','base','auth','content','media','search','learning','orders','gateway')) {
    mvn -f "$module/pom.xml" install
    if ($LASTEXITCODE -ne 0) { throw "$module 构建失败" }
}
# 在独立终端启动各服务，示例：
mvn -f content/content_api/pom.xml spring-boot:run
npm --prefix frontend-admin ci
npm --prefix frontend-student ci
# 两个独立终端分别执行：
npm --prefix frontend-admin run dev
npm --prefix frontend-student run dev
# 辅助编码检查脚本已在仓库精简时移除。
```

分别使用各 API 模块的 pom.xml 执行 spring-boot:run：auth/auth_api、content/content_api、media/media_api、search/search_api、learning/learning_api、orders/orders_api；Gateway 使用 gateway/pom.xml。启动前按上文配置进程环境变量。支付宝本地配置如需加载，执行 orders 启动命令时增加参数 -Dspring-boot.run.arguments="--spring.config.additional-location=optional:file:../../.runtime/alipay-sandbox.properties"。启动后用真实接口验证。

| 服务          | 端口      | 数据                 |
| ------------- | --------- | -------------------- |
| Gateway       | 63010     | Nacos路由            |
| Content       | 63040     | class                |
| Media         | 63050     | media与S3            |
| Search        | 63060     | Elasticsearch、Redis |
| Learning      | 63070     | learning             |
| Auth          | 63080     | users                |
| Orders        | 63090     | orders               |
| 管理端/学员端 | 5173/5174 | 经Gateway真实API     |

联调顺序：注册学员→申请成为老师→平台审核开通老师账号→老师上传视频并转码→建课绑定→平台管理员审核→发布→Search/Learning收到课程副本→免费选课播放；收费课程选课→订单→沙箱二维码→真实沙箱付款→验签/主动查询→payment_event→Learning事务开通→回执→受保护播放。课程审核和老师申请审核统一由平台管理员执行，老师不能审核自己的课程。

## 支付沙箱

使用[支付宝官方SDK](https://github.com/alipay/alipay-sdk-java-all)，common/alipay-dev.yaml中配置 app-id、private-key、public-key、seller-id，键位于 sunflower.alipay。仅允许官方沙箱网关。notify-url为沙箱可访问的公开HTTPS回调，路径 /orders/payments/alipay/notify；没有公开回调时可主动查询恢复支付状态，但异步真实回调验收仍需可达地址。不要把密钥放入聊天、源码或截图。

付款按钮只生成平台二维码，不能直接开通资格。缺配置返回503；网络或签名失败不伪造支付成功。30分钟到期先查询/关闭平台交易；平台不可用时保留可恢复状态。支付金额、应用、收款账号、流水及实际支付时间核对一致后才确认；截止前真实支付但迟到的通知允许恢复。支付状态与可靠事件同事务；学习提交后回执，事件按订单去重且不延长有效期。失败队列 payment.learning.failed.queue保留消息，可通过RabbitMQ管理界面重新发布原消息到 payment.learning 路由；订单事件在收到学习回执前持续重试。

## CI

GitHub CI 工作流已在仓库精简时移除。以下数据库测试要求及验收结果保留为历史说明。

本地运行Learning关键测试需设置 MYSQL_TEST_URL（learning库）、MYSQL_TEST_USER、MYSQL_TEST_PASSWORD。测试创建专用记录并回滚，不修改原用户资格。未设置连接时该组测试会明确跳过；不能据此宣称真实数据库验证通过。本轮已在现有learning与新建隔离空库分别运行通过。GitHub工作流尚未提交触发，当前结果属于本机等价校验。

隔离MySQL已实测45张表初始化两次、用户/订单/选课业务数据均为空，随后3项资格关键测试通过。Compose已进行原生配置校验；整套新Compose容器未启动，新环境端到端验收仍按上述步骤执行。

## 2026-10-03 隔离全新部署实测

见[全新部署验收单](ACCEPTANCE_FRESH_DEPLOYMENT.md)。独立Compose项目与端口、新数据卷完成空库初始化、Nacos模板导入及鉴权、七个业务服务、两端代理、真实上传转码、审核发布、MQ同步、免费选课和私有视频播放，并完成第二个空MySQL的六库备份恢复。

本轮对象存储使用PGSTY维护的MinIO社区分支pgsty/silo，并固定镜像摘要；Compose默认镜像已同步为本轮验证的pgsty/silo固定摘要，用户明确不使用minio/minio，不再将其拉取验证列为待办。初始平台与课程审核账号是在新users库受控创建的随机账号；原数据库和对象未复制。隔离命令覆盖位于.runtime/deploy-check-20261003，RabbitMQ非标准端口通过业务进程的--spring.rabbitmq.port=25672传入，前端通过已有VITE_GATEWAY_TARGET切换，Nacos鉴权环境必须同时传入Windows Java进程。
