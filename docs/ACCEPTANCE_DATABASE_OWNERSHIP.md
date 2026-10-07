# 数据库归属整理验收单

日期：2026-10-01。状态：技术验证通过，等待用户验收。本次只整理数据库归属，不推进下一项上传或支付功能。

## 结果

- class 由16张表整理为12张，仅保留课程管理、教学计划及发布消息。
- learning_course 的8条正式目录副本、course_enrollment 的2条选课记录，原生移动到独立 learning 库；与先前导入的3张历史表共存，Learning只使用现行两表，不把旧记录合并为资格。
- auth_user 的2条和 search_course 的4条完整移动到 class_archive_20261001，保留原表结构和数据；Auth继续读取users，Search继续使用Elasticsearch。
- Nacos learning-service-dev.yaml只改变数据库名class→learning，连接参数与凭据保留；Content继续用class，业务三层代码无需改动。

## 验证

1. 迁移前暂停Content/Learning，备份四表原结构/数据、配置及逐表统计，单条MySQL原生RENAME移动四表。所有原class表及此前learning历史三表的实际行数和CHECKSUM TABLE EXTENDED均核对一致。
2. 实际重放课程159的已保存事件42和旧下架事件39，通过真实RabbitMQ、Search和Learning消费，learning.learning_course仍为event_id=42、status=30502，不被旧事件覆盖。正常回执更新消息确认时间，未改写事件快照。
3. 18项真实HTTP断言通过：原账号登录、Content课程读取、Elasticsearch详情、Learning目录、免费/待支付资格、重复选课不变价/不续期、课程表分页、匿名401、机构403、待支付播放403、有效学员播放地址及实际MP4 Range 206。
4. 浏览器使用原验收学员登录，看到2门课程，免费课程可学习、收费课程待支付¥19.9；免费到期时间仍为2026-10-31 12:26:54。点击课程159原生播放按钮后readyState=4、currentTime=0.168182、paused=false、无视频错误。
5. 配置及中文说明统一UTF-8，执行乱码检查；数据移动不重建记录，不改变价格、有效期或用户权限。

## 人工验收

使用原账号登录 http://localhost:5174/my-courses，确认两条记录和免费课程播放正常。查看数据库：class应有12张表；learning共5张表，现用两表及历史三表；class_archive_20261001含原auth_user和search_course。

备份与实时验证在忽略目录.runtime/db-separation-20261001，含私有配置及旧账号散列，不能提交或对外展示。部署、原生迁移脚本和回滚步骤见 [数据库归属说明](DATABASE_OWNERSHIP.md)。用户确认后仍等待“继续”才开发下一项。
