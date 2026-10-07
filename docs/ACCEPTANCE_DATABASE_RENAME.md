# 课程数据库改名验收

日期：2026-10-01。状态：改名及真实前后端检查完成，用户于 2026-10-01 确认验收通过。本次仅将数据库 `sunflower_class` 改为 `class`，不继续选课开发。

## 结果

- 原库 19 张表均为 InnoDB，无视图、触发器、存储过程或外键依赖；目标 class 库原先不存在。暂停 Content 和 Learning 后，备份表定义及数据，使用单条 MySQL 原生 RENAME TABLE 整体移动，保留表名、字段、索引和自增设置，没有重建业务表。
- 新库字符集/排序规则保持 utf8mb4 / utf8mb4_0900_ai_ci。迁移前后逐表实际记录数与 CHECKSUM TABLE EXTENDED 校验值全部一致，总计 647 条记录，含原备份表及历史 auth_user。
- Nacos 的 dev / sunflower_class 下，content-service-dev.yaml 和 learning-service-dev.yaml 仅更新 JDBC URL 中的库名为 class；连接地址、凭据及其余内容保持原值。Content、Learning 已重启，读取真实新库。
- users、media、system 数据库不变。Nacos 分组 sunflower_class、com.sunflower_class Java 包及 Elasticsearch 索引属于其他标识，不随数据库名替换。业务代码不需要修改：No code changes are required for this task.
- 校验旧库无表、存储过程、事件后，移除空 sunflower_class 库。没有删除业务记录。现有课程库仅显示 class。

## 备份与恢复边界

完整 UTF-8 SQL 数据备份：`.runtime/db-rename-20261001/sunflower_class-backup.sql`。两份原 Nacos 配置以及迁移前后记录数/校验值也保存在该忽略目录；含敏感历史账号散列及配置凭据，不提交仓库。

新库已继续承接业务。需要恢复旧名称时，先暂停相关服务并备份新写入数据，再用原生语句反向移动各表、恢复原连接配置并重启。SQL 全量备份仅供恢复使用，不能向已有业务表直接重复导入。

原生跨库移动方式依据 [MySQL RENAME TABLE 文档](https://dev.mysql.com/doc/refman/8.0/en/rename-table.html)，迁移前已核查相关限制。

## 实际验证

- 19 张表全部记录数和校验值一致；新配置 JDBC 连接的 DATABASE() 返回 class。
- 经真实 Vite/Gateway 执行机构/学生登录、课程与媒资访问、角色拒绝、直连服务授权及公开搜索 14 项检查，全部通过。
- 审核员队列可读取四条已通过课程及其真实提交快照；公开 Search 课程与 Learning 目录请求成功。
- 实际浏览器打开学员端课程 121，显示 Spring Cloud 开发实战、两章七节的真实目录，中文正常。
- 部署文档、配置注释与验收文档统一 UTF-8，严格解码和乱码检查通过；未修改 Java/Vue 业务逻辑，无需重新构建应用。

## 用户验收

1. 刷新数据库管理工具，确认 class 中有完整的 19 张表，旧 sunflower_class 库已不在列表中。
2. 在机构端登录并查看本机构课程、审核状态和教学计划；用审核账号查看已通过快照。
3. 在学员端打开已发布课程，确认标题及教学目录正常显示。

本次数据库改名已由用户确认验收通过；等待用户明确说“继续”后再开始下一项。上一项角色权限的验收状态保留在其独立验收单中。
