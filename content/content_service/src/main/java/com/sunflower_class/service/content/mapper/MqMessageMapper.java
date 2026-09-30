package com.sunflower_class.service.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.po.MqMessage;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 复用 MyBatis-Plus 的写入能力，将课程发布消息保存到内容库现有的 mq_message 表。 */
public interface MqMessageMapper extends BaseMapper<MqMessage> {
    /** 扫描未完成消息；一分钟租约让多实例不会同时抢占同一发送任务。 */
    @Select(
        "SELECT * FROM mq_message WHERE message_type='course_publish' AND state <> '1' AND payload IS NOT NULL AND execute_num < 10 AND (execute_date IS NULL OR execute_date < DATE_SUB(NOW(), INTERVAL 60 SECOND)) ORDER BY id LIMIT 20"
    )
    List<MqMessage> pending();

    /** 原子抢占并增加执行次数；进程意外退出后租约到期自动恢复。 */
    @Update(
        "UPDATE mq_message SET execute_num=execute_num+1, execute_date=NOW() WHERE id=#{id} AND state <> '1' AND execute_num < 10 AND (execute_date IS NULL OR execute_date < DATE_SUB(NOW(), INTERVAL 60 SECOND))"
    )
    int claim(long id);

    /** 阶段一仅表示队列确认，总成功必须等两个消费者回执。 */
    @Update(
        "UPDATE mq_message SET stage_state1='1', state=IF(stage_state3='1' AND stage_state4='1','1',IF(stage_state3='2' OR stage_state4='2' OR execute_num>=10,'2','0')), returnsuccess_date=IF(stage_state3='1' AND stage_state4='1',NOW(),NULL) WHERE id=#{id}"
    )
    void sent(long id);

    /** 发送失败持久化原因；晚到的成功回执不能被旧发送错误覆盖。 */
    @Update(
        "UPDATE mq_message SET stage_state1=IF(stage_state1='1','1','2'), state=IF(state='1','1','2'), returnfailure_date=NOW(), returnfailure_msg=#{reason} WHERE id=#{id}"
    )
    void failed(@Param("id") long id, @Param("reason") String reason);

    /** 搜索回执只修改搜索阶段，避免并发回执丢失另一阶段的成功结果。 */
    @Update(
        "UPDATE mq_message SET stage_state3='1', state=IF(stage_state1='1' AND stage_state4='1','1',state), returnsuccess_date=IF(stage_state1='1' AND stage_state4='1',NOW(),returnsuccess_date) WHERE id=#{id} AND message_type='course_publish'"
    )
    void searchDone(long id);

    /** 学习回执只修改学习阶段；Redis 阶段尚未开发，保持初始状态。 */
    @Update(
        "UPDATE mq_message SET stage_state4='1', state=IF(stage_state1='1' AND stage_state3='1','1',state), returnsuccess_date=IF(stage_state1='1' AND stage_state3='1',NOW(),returnsuccess_date) WHERE id=#{id} AND message_type='course_publish'"
    )
    void learningDone(long id);

    /** 消费失败保留原因，但不能覆盖已经确认成功的同一阶段。 */
    @Update(
        "UPDATE mq_message SET stage_state3=IF(stage_state3='1','1','2'), state=IF(state='1','1','2'), returnfailure_date=NOW(), returnfailure_msg=#{reason} WHERE id=#{id} AND message_type='course_publish' AND stage_state3<>'1'"
    )
    void searchFailed(@Param("id") long id, @Param("reason") String reason);

    /** 学习失败回执独立更新，后续成功回执仍可恢复总状态。 */
    @Update(
        "UPDATE mq_message SET stage_state4=IF(stage_state4='1','1','2'), state=IF(state='1','1','2'), returnfailure_date=NOW(), returnfailure_msg=#{reason} WHERE id=#{id} AND message_type='course_publish' AND stage_state4<>'1'"
    )
    void learningFailed(@Param("id") long id, @Param("reason") String reason);

    /** 机构只可重新安排本机构的未完成事件，不改变已有成功回执。 */
    @Update(
        "UPDATE mq_message SET execute_num=0, execute_date=NULL, state='0' WHERE id=#{id} AND business_key2=#{companyId} AND message_type='course_publish' AND state<>'1' AND payload IS NOT NULL"
    )
    int retry(@Param("id") long id, @Param("companyId") String companyId);
}
