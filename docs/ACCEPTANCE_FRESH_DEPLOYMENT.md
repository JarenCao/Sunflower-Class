# 全新环境部署验收记录

日期：2026-10-03。按用户要求，跳过真实沙箱付款，逐项校验并等待人工验收。本轮只推进全新环境部署，不推进下一项。

## 当前结论与边界

**独立空库 + 新建MinIO社区分支Silo对象存储 + 独立Nacos/MQ/Redis/ES的免费课程全链路技术验证通过，等待用户验收。对象存储采用已验证的pgsty/silo固定摘要，已同步为Compose默认镜像。**

默认minio/minio:RELEASE.2025-04-22T22-12-26Z实际下载最终出现connection reset by peer；官方Quay同版本请求返回401。没有伪装下载成功、没有使用旧对象数据。本轮使用PGSTY维护的MinIO社区分支Silo，复用本机已有镜像，固定镜像摘要、新建空数据卷并使用新随机凭据。用户已明确不使用minio/minio；源Compose已改用本轮验证的pgsty/silo固定摘要，隔离环境覆盖文件保留作为验证记录。

镜像：`pgsty/silo@sha256:29a498b24669cae1fed11c1a2fb2b3d73c68829a0a9c0b14e71b386671d38fac`。项目来源：[PGSTY Silo（MinIO社区分支）](https://github.com/pgsty/silo)。兼容接口依据：[Silo部署说明](https://silo.pgsty.com/operations/deployments/baremetal-deploy-minio-as-a-container/)。该镜像已通过本轮对象存储链路校验；本轮验证不等同于生产高可用验收。

## 隔离措施

独立Compose项目sunflower-deploy-check-20261003；所有新端口只绑定本机，新数据卷使用项目名前缀；没有复制旧用户、课程、订单、资格或对象。Nacos独立创建dev命名空间并导入19个仓库配置模板，开启鉴权并更换默认密码。每个Java进程使用独立Nacos及随机凭据；两个Vite实例通过已有VITE_GATEWAY_TARGET指向独立网关。

| 服务                                                | 隔离端口                                      |
| --------------------------------------------------- | --------------------------------------------- |
| MySQL / 恢复验证MySQL                               | 23306 / 23307                                 |
| Nacos HTTP / gRPC                                   | 28848 / 29848                                 |
| RabbitMQ AMQP / 管理                                | 25672 / 25673                                 |
| Redis / Elasticsearch                               | 26379 / 29200                                 |
| S3 API / 控制台                                     | 29000 / 29001                                 |
| Gateway                                             | 63110                                         |
| Content / Media / Search / Learning / Auth / Orders | 63140 / 63150 / 63160 / 63170 / 63180 / 63190 |
| 管理端 / 学员端                                     | 5183 / 5184                                   |

WSL内存较少，隔离Nacos使用256m堆、ES沿用512m堆，Java服务使用256m堆；仅用于本机功能演练，不是生产容量建议。原有服务、端口和数据卷保持运行；原5173、5174、Gateway公开搜索均HTTP200复验通过。第二个恢复MySQL已停止释放资源，数据卷保留。

## 本次发现并修复

新库建章后教学计划查询500，真实日志指出TeachplanMedia没有coursePubId的setter。搜索模型、Mapper、SQL和业务引用后，确认是树形Mapper媒资association的错误属性，不需要补虚假字段或新工具。

最小修改content/content_service/src/main/resources/com/sunflower_class/service/content/mapper/TeachplanMapper.xml：删除媒资关联中的coursePubId映射，使用MyBatis原生notNullColumn=teachplanMediaId，仅在存在真实绑定时创建媒资关联。未绑定小节不返回虚假的媒资对象；按现有JSON空值省略约定，teachplanMedia可以省略。未改教学计划本身的coursePubId字段。

修复后：章、小节目录查询正常；未绑定小节空关联、绑定后真实mediaId、提审快照、发布快照、学习目录和授权链路均复验。Content构建成功；构建使用-DskipTests，运行校验由本轮真实接口断言承担。仅重启隔离Content加载修复，没有重启原Content。

## 校验结果

- 8项基础设施断言：Compose自动初始化六库45张表；用户/订单/选课/课程业务为空；重复初始化表数量和业务空数据不变；Redis密码认证；RabbitMQ sunflower虚拟主机；ES健康；Nacos匿名配置读取403。
- 15项账户/服务发现断言：新注册登录、用户名重复拒绝、空ES、机构申请、学生不能平台审批、受控平台管理员审批、新机构管理员登录、老师创建登录和机构绑定、普通老师不能管理账号、Content/Orders经Gateway可访问。
- 26项课程流程断言：新S3上传图片/视频、真实WSL Docker FFmpeg转码、建课/建章/建节、未绑定/已绑定Mapper回归、媒资绑定、独立审核、教师自审拒绝、发布、真实RabbitMQ同步ES和Learning、无资格播放拒绝、免费选课及重复幂等、我的课程、签名授权、私有MP4 Range206字节、对象匿名403、下架后搜索404及拒绝新播放授权。
- 六库备份恢复到第二个空MySQL：45张表、业务行数、用户密码散列、角色和老师关联逐行一致。恢复备份取自账户流程完成后，未包含后续课程；未测试对象存储灾难恢复。
- 3项现有Learning真实MySQL测试通过，Failures=0、Errors=0、Skipped=0，在新隔离learning库事务回滚；这是初始化结构与Mapper兼容校验，不是实际支付证明。
- 两端vue-tsc和生产构建通过；管理端存在大于500kB的构建包提示，本轮不扩展到性能拆包任务。
- 两端隔离HTML和真实API代理登录通过；源码及文档UTF-8检查通过。

过程中的验收脚本有两处断言按现有接口约定修正：平台审批成功是204，IdentityDto使用name而不是username；不属于业务缺陷。每次中断后沿用专用验收记录，不通过修改业务状态伪造结果。

## 人工验收

隔离入口：[管理端](http://localhost:5183)、[学员端](http://localhost:5184)。专用课程ID=1，名称“全新部署验收课程”，下架验证后已重新发布供人工查看。

专用账号位于本机忽略文件.runtime/deploy-check-20261003/workflow-accounts.json（student/manager/teacher）；初始平台和审核员在initial-accounts.json。密码不复制到本文。可以登录学员端检查课程、我的学习和真实视频；登录管理端检查课程计划及绑定；也可以另行注册验证空环境自主开户。

结果、隔离配置、启动脚本、备份均保存在.runtime/deploy-check-20261003。当前验收环境保留运行，不自动删除数据卷。验证入口仅是Vite开发服务器；生产前端HTTP托管、Linux应用容器部署、生产监控与对象存储恢复不在本次通过范围。真实付款、异步支付回调按用户要求跳过，保持未验收。

镜像选型已确定为pgsty/silo，取消minio/minio拉取与复验待办。全新部署技术校验已完成，等待用户验收，暂不推进下一项。
