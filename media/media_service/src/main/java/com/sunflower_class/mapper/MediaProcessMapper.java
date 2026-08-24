package com.sunflower_class.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.po.MediaProcess;

public interface MediaProcessMapper extends BaseMapper<MediaProcess> {

    /**
     * 原子性更新状态
     * 只有当前状态为 targetStatus 时才能更新
     * 防止并发重复处理
     */
    @Update("UPDATE media_process SET status = #{newStatus}, errormsg = NULL "
            + "WHERE id = #{id} AND status IN ('1', '3') AND fail_count < #{failCount}")
    int updateStatusIfProcessing(@Param("id") String id, @Param("failCount") int fail_count,
            @Param("newStatus") String newStatus);

    /**
     * 查询超过最大重试次数的失败任务
     * 状态为3（失败待重试），且失败次数 >= 5
     * 用于定时任务查询
     */
    @Select("SELECT * FROM media_process " +
            "WHERE status = '3' AND fail_count >= #{failCount} " +
            "ORDER BY create_date ASC " +
            "LIMIT #{limit}")
    List<MediaProcess> selectShedulerTasks(@Param("failCount") int fail_count, @Param("limit") int limit);

    /**
     * 更新超过最大重试次数的失败任务
     * 状态为3（失败待重试），且失败次数 >= 5
     * 用于定时任务重试
     */
    @Update("UPDATE media_process SET status='1', errormsg = NULL " +
            "WHERE id = #{id} AND status='3' AND fail_count >= #{failCount} " +
            "LIMIT #{limit}")
    int updateStatusFailTask(@Param("id") Long id,
            @Param("failCount") int failCount,
            @Param("limit") int limit);

}
