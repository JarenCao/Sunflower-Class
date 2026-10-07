package com.sunflower_class.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunflower_class.model.po.MediaFiles;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 媒资文件的数据访问映射；通用增删改查由 MyBatis-Plus 提供，自定义查询另行声明。
 */
public interface MediaFilesMapper extends BaseMapper<MediaFiles> {
    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT status,COALESCE(fail_count,0) AS failCount,errormsg AS error,retry_at AS retryAt FROM media_process WHERE file_id=#{fileId} UNION ALL SELECT status,COALESCE(fail_count,0),errormsg,NULL FROM media_process_history WHERE file_id=#{activeFileId} LIMIT 1"
    )
    List<Map<String, Object>> selectFileProcessStatus(
        @Param("fileId") String fileId,
        @Param("activeFileId") String activeFileId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT id,status FROM media_files WHERE id=#{id} AND company_id=#{companyId} FOR UPDATE"
    )
    List<Map<String, Object>> selectOwnedFileForUpdate(
        @Param("id") String id,
        @Param("companyId") Long companyId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_process SET status='20301',fail_count=0,retry_at=NULL,dispatch_at=NULL,errormsg=NULL WHERE file_id=#{fileId} AND status='20303'"
    )
    int resetFileProcesses(@Param("fileId") String fileId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update("UPDATE media_files SET status='20301' WHERE id=#{id}")
    int markFileWaiting(@Param("id") String id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_files SET status='20305',delete_error=NULL WHERE id=#{id} AND company_id=#{companyId}"
    )
    int markFileDeleting(@Param("id") String id, @Param("companyId") Long companyId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update(
        "UPDATE media_process SET status='20305',processing_at=NULL WHERE file_id=#{fileId} AND status<>'20304'"
    )
    int cancelFileProcesses(@Param("fileId") String fileId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select("SELECT COUNT(*) FROM media_process WHERE file_id=#{fileId} AND status='20304'")
    Long countRunningProcesses(@Param("fileId") String fileId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update("UPDATE media_files SET delete_error='正在取消转码，请稍后重试清理' WHERE id=#{id}")
    int markCleanupPending(@Param("id") String id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Update("UPDATE media_files SET delete_error=#{error} WHERE id=#{id}")
    int markCleanupError(@Param("error") String error, @Param("id") String id);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT bucket,file_path AS path FROM media_process WHERE file_id=#{fileId} UNION SELECT bucket,file_path AS path FROM media_process_history WHERE file_id=#{historyFileId}"
    )
    List<Map<String, Object>> selectFileObjects(
        @Param("fileId") String fileId,
        @Param("historyFileId") String historyFileId
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Select(
        "SELECT (SELECT COUNT(*) FROM media_files WHERE id<>#{id} AND bucket=#{bucket} AND file_path=#{path}) + (SELECT COUNT(*) FROM media_process_history h JOIN media_files f ON f.id=h.file_id WHERE f.id<>#{historyOwnerId} AND h.bucket=#{historyBucket} AND h.file_path=#{historyPath}) + (SELECT COUNT(*) FROM media_process p JOIN media_files f ON f.id=p.file_id WHERE f.id<>#{processOwnerId} AND p.bucket=#{processBucket} AND p.file_path=#{processPath})"
    )
    Long countSharedObjects(
        @Param("id") String id,
        @Param("bucket") String bucket,
        @Param("path") String path,
        @Param("historyOwnerId") String historyOwnerId,
        @Param("historyBucket") String historyBucket,
        @Param("historyPath") String historyPath,
        @Param("processOwnerId") String processOwnerId,
        @Param("processBucket") String processBucket,
        @Param("processPath") String processPath
    );

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Delete("DELETE FROM media_process WHERE file_id=#{fileId}")
    int deleteFileProcesses(@Param("fileId") String fileId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Delete("DELETE FROM media_process_history WHERE file_id=#{fileId}")
    int deleteFileHistory(@Param("fileId") String fileId);

    /** 参数绑定并保留原状态、事务和锁定条件。 */
    @Delete("DELETE FROM media_files WHERE id=#{id} AND status='20305'")
    int deleteDeletingFile(@Param("id") String id);
}
