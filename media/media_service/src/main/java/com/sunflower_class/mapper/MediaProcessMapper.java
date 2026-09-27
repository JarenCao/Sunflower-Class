package com.sunflower_class.mapper;

import static com.sunflower_class.base.model.BusinessCodes.*;

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
        "UPDATE media_process SET status = #{newStatus}, errormsg = NULL " +
            "WHERE id = #{id} AND status IN ('" +
            PROCESS_WAITING +
            "', '" +
            PROCESS_FAILED +
            "') AND COALESCE(fail_count, 0) < #{failCount}"
    )
    int updateStatusIfProcessing(
        @Param("id") String id,
        @Param("failCount") int fail_count,
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
        @Param("failCount") int fail_count,
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
}
