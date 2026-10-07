package com.sunflower_class.mapper;

import static com.sunflower_class.base.model.BusinessCodes.PROCESS_FAILED;
import static com.sunflower_class.base.model.BusinessCodes.PROCESS_WAITING;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.po.MediaProcess;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 视频处理任务的数据访问映射；通用增删改查由 MyBatis-Plus 提供，自定义查询另行声明。
 */
public interface MediaProcessMapper extends BaseMapper<MediaProcess> {
    /**
     * 原子性更新状态
     * 仅待处理或失败且未达重试上限的任务允许更新为传入的新状态
     * 防止并发重复处理
     */
    @Update(
        "UPDATE media_process SET status = #{newStatus}, errormsg = NULL, processing_at=NOW() " +
            "WHERE id = #{id} AND status='" +
            PROCESS_WAITING +
            "' AND COALESCE(fail_count, 0) < #{failCount}"
    )
    int updateStatusIfProcessing(
        @Param("id") String id,
        @Param("failCount") int failCount,
        @Param("newStatus") String newStatus
    );

    /**
     * 查询仍可重试的失败任务
     * 失败状态，且失败次数小于配置上限
     * 用于定时任务查询
     */
    @Select(
        "SELECT * FROM media_process " +
            "WHERE status = '" +
            PROCESS_FAILED +
            "' AND COALESCE(fail_count, 0) < #{failCount} " +
            "ORDER BY create_date ASC " +
            "LIMIT #{limit}"
    )
    List<MediaProcess> selectShedulerTasks(
        @Param("failCount") int failCount,
        @Param("limit") int limit
    );

    /**
     * 更新仍可重试的失败任务
     * 失败状态，且失败次数小于配置上限
     * 用于定时任务重试
     */
    @Update(
        "UPDATE media_process SET status='" +
            PROCESS_WAITING +
            "', errormsg = NULL " +
            "WHERE id = #{id} AND status='" +
            PROCESS_FAILED +
            "' AND COALESCE(fail_count, 0) < #{failCount} " +
            "LIMIT #{limit}"
    )
    int updateStatusFailTask(
        @Param("id") Long id,
        @Param("failCount") int failCount,
        @Param("limit") int limit
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_files f JOIN media_process p ON f.id=p.file_id SET f.status='20303' WHERE f.status<>'20305' AND p.status='20304' AND p.processing_at < DATE_SUB(NOW(),INTERVAL #{timeoutMinutes} MINUTE)"
    )
    int markStalledFilesFailed(@Param("timeoutMinutes") Long timeoutMinutes);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_process p JOIN media_files f ON f.id=p.file_id SET p.status='20303',p.fail_count=COALESCE(p.fail_count,0)+1,p.retry_at=NOW(),p.errormsg='处理中任务超时，已安排恢复',p.processing_at=NULL WHERE f.status<>'20305' AND p.status='20304' AND p.processing_at < DATE_SUB(NOW(),INTERVAL #{timeoutMinutes} MINUTE)"
    )
    int markStalledProcessesFailed(@Param("timeoutMinutes") Long timeoutMinutes);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_process p JOIN media_files f ON f.id=p.file_id SET p.status='20305',p.processing_at=NULL WHERE f.status='20305' AND p.status='20304' AND p.processing_at<DATE_SUB(NOW(),INTERVAL #{timeoutMinutes} MINUTE)"
    )
    int cancelDeletedProcesses(@Param("timeoutMinutes") Long timeoutMinutes);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT id FROM media_process WHERE COALESCE(fail_count,0) < #{maxAttempts} AND ((status='20301' AND (dispatch_at IS NULL OR dispatch_at<DATE_SUB(NOW(),INTERVAL 60 SECOND))) OR (status='20303' AND (retry_at IS NULL OR retry_at<=NOW()))) ORDER BY create_date LIMIT #{limit}"
    )
    List<Long> selectDispatchableTasks(
        @Param("maxAttempts") Integer maxAttempts,
        @Param("limit") Integer limit
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_process SET status='20301',dispatch_at=NOW() WHERE id=#{id} AND COALESCE(fail_count,0) < #{maxAttempts} AND ((status='20301' AND (dispatch_at IS NULL OR dispatch_at<DATE_SUB(NOW(),INTERVAL 60 SECOND))) OR (status='20303' AND (retry_at IS NULL OR retry_at<=NOW())))"
    )
    int claimDispatch(@Param("id") Long id, @Param("maxAttempts") Integer maxAttempts);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_files f JOIN media_process p ON f.id=p.file_id SET f.status='20301' WHERE p.id=#{processId}"
    )
    int markDispatchedFileWaiting(@Param("processId") Long processId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_process SET errormsg='转码消息投递失败，租约到期后重投' WHERE id=#{id} AND status='20301'"
    )
    int markDispatchFailure(@Param("id") Long id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_process SET processing_at=NULL,retry_at=DATE_ADD(NOW(),INTERVAL #{delaySeconds} SECOND) WHERE id=#{id}"
    )
    int scheduleProcessRetry(@Param("delaySeconds") Integer delaySeconds, @Param("id") Long id);
}
