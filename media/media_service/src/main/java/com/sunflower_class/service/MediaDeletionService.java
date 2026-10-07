package com.sunflower_class.service;

import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.base.utils.MediaObjectLock;
import com.sunflower_class.config.MinioConfig;
import com.sunflower_class.mapper.MediaFilesMapper;
import com.sunflower_class.model.po.MediaFiles;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** 删除先持久化标记，再取消转码和清理存储，最后删除记录；失败保留原路径可恢复。 */
@Service
public class MediaDeletionService {

    @Autowired
    private MediaFilesMapper files;

    @Autowired
    private DataSource source;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private DiscoveryClient discovery;

    @Autowired
    private MinioClient minio;

    @Autowired
    private MinioConfig config;

    /** 复用现有数据源、服务发现、事务与MinIO客户端。 */

    /** 只允许当前机构检查自己的媒资，未知及其他机构编号均返回404。 */
    private MediaFiles owned(long company, String id) {
        MediaFiles file = files.selectById(id);
        if (
            file == null || !Objects.equals(file.getCompanyId(), company)
        ) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "媒资不存在或不属于本机构");
        return file;
    }

    /** 经Content服务核对全部引用，不能因课程服务故障而误判为无引用。 */
    public Map<String, Long> references(long company, String id) {
        MediaFiles file = owned(company, id);
        List<ServiceInstance> instances = discovery.getInstances("content-api");
        if (instances.isEmpty()) throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "内容服务不可用，无法核对引用"
        );
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        try {
            Map result = RestClient.builder()
                .baseUrl(instances.getFirst().getUri().toString())
                .requestFactory(factory)
                .build()
                .get()
                .uri(builder ->
                    builder
                        .path("/content/media-references/{id}")
                        .queryParam("url", file.getUrl() == null ? "" : file.getUrl())
                        .build(id)
                )
                .header("Authorization", "Bearer " + CurrentUser.jwt().getTokenValue())
                .retrieve()
                .body(Map.class);
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String key : List.of("bindings", "covers", "published", "audit")) {
                if (
                    result == null || !(result.get(key) instanceof Number value)
                ) throw new IllegalStateException("引用响应无效");
                counts.put(key, value.longValue());
            }
            return counts;
        } catch (Exception error) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "暂时无法核对媒资引用，请稍后重试"
            );
        }
    }

    /** 共享命名锁覆盖引用检查与删除，等待正在提交的新绑定。 */
    public boolean delete(long company, String id) {
        try (MediaObjectLock lock = new MediaObjectLock(source, id)) {
            MediaFiles file = owned(company, id);
            if (!"20305".equals(file.getStatus())) {
                Map<String, Long> refs = references(company, id);
                if (
                    refs
                        .values()
                        .stream()
                        .anyMatch(count -> count > 0)
                ) throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "文件仍被教学计划、封面、审核快照或发布课程引用，请先解除引用"
                );
                transactions.executeWithoutResult(transaction -> {
                    files.markFileDeleting(id, company);
                    // 未开始任务直接停止；正在运行的任务读取删除标记后取消本次FFmpeg。
                    files.cancelFileProcesses(id);
                });
            }
            try {
                for (int wait = 0; wait < 15; wait++) {
                    Long running = files.countRunningProcesses(id);
                    if (running == null || running == 0) return cleanup(file);
                    Thread.sleep(1000);
                }
                files.markCleanupPending(id);
                return false;
            } catch (Exception error) {
                files.markCleanupError("文件清理失败：" + error.getClass().getSimpleName(), id);
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "清理未完成，记录已保留，请重试清理"
                );
            }
        }
    }

    /** 清理原文件、转码文件与历史路径；他人仍使用的共享对象必须保留。 */
    private boolean cleanup(MediaFiles file) throws Exception {
        List<Map<String, Object>> objects = files.selectFileObjects(file.getId(), file.getId());
        // 历史记录可能缺少存储位置；保留有效路径并跳过空值，避免清理永久卡住。
        objects.add(
            Map.of(
                "bucket",
                Objects.toString(file.getBucket(), ""),
                "path",
                Objects.toString(file.getFilePath(), "")
            )
        );
        objects.add(
            Map.of("bucket", config.getVideofiles(), "path", "transcoded/" + file.getId() + ".mp4")
        );
        Set<String> removed = new HashSet<>();
        for (Map<String, Object> object : objects) {
            String bucket = Objects.toString(object.get("bucket"), "");
            String path = Objects.toString(object.get("path"), "");
            if (bucket.isBlank() || path.isBlank() || !removed.add(bucket + "/" + path)) continue;
            Long shared = files.countSharedObjects(
                file.getId(),
                bucket,
                path,
                file.getId(),
                bucket,
                path,
                file.getId(),
                bucket,
                path
            );
            if (shared != null && shared > 0) continue;
            minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(path).build());
        }
        transactions.executeWithoutResult(transaction -> {
            files.deleteFileProcesses(file.getId());
            files.deleteFileHistory(file.getId());
            files.deleteDeletingFile(file.getId());
        });
        return true;
    }
}
