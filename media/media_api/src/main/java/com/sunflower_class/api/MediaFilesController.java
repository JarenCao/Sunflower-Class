package com.sunflower_class.api;

import static com.sunflower_class.base.model.BusinessCodes.FILE_IMAGE;
import static com.sunflower_class.base.model.BusinessCodes.FILE_VIDEO;

import com.sunflower_class.base.model.PageParams;
import com.sunflower_class.base.model.PageResult;
import com.sunflower_class.base.security.CurrentUser;
import com.sunflower_class.model.dto.PlaybackUrlDto;
import com.sunflower_class.model.dto.QueryMediaParamsDto;
import com.sunflower_class.model.dto.UploadFileParamsDto;
import com.sunflower_class.model.dto.UploadFileResultDto;
import com.sunflower_class.model.po.MediaFiles;
import com.sunflower_class.service.MediaFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒资查询、普通上传与文件访问接口，通过短期签名地址访问对象存储。
 */
@Tag(name = "媒资文件管理", description = "媒资文件管理接口")
@CrossOrigin(origins = "*")
@RestController
public class MediaFilesController {

    /** 课程封面解析到已有媒资ID，用于保存封面与删除之间的共享锁。 */
    @Operation(
        summary = "解析站内课程封面",
        description = "核对本人教学空间的媒资，用于封面保存与删除互斥校验。"
    )
    @GetMapping("/files/cover-info")
    public Map<String, Object> coverInfo(@RequestParam String url) {
        return mediaFileService.coverInfo(CurrentUser.companyId(), url);
    }

    /** 删除确认前显示真实引用数量，实际删除时服务端再次检查。 */
    @Operation(
        summary = "查询媒资引用数量",
        description = "统计草稿、审核及正式快照的引用，删除时再次核对。"
    )
    @GetMapping("/files/{id}/references")
    public Map<String, Long> references(@PathVariable String id) {
        return mediaFileService.references(CurrentUser.companyId(), id);
    }

    /** 202表示正在取消转码；204仅在对象与记录均清理后返回。 */
    @Operation(
        summary = "删除媒资文件",
        description = "202 表示正在取消转码，204 表示对象和记录已清理完成。"
    )
    @DeleteMapping("/files/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return ResponseEntity.status(
            mediaFileService.deleteMedia(CurrentUser.companyId(), id) ? 204 : 202
        ).build();
    }

    /** 返回当前机构任务状态，供媒资中心显示失败原因和次数。 */
    @Operation(
        summary = "查询视频转码任务状态",
        description = "返回本人教学空间的转码状态、失败原因和重试次数。"
    )
    @GetMapping("/files/{id}/process")
    public Map<String, Object> process(@PathVariable String id) {
        return mediaFileService.process(CurrentUser.companyId(), id);
    }

    /** 人工重新处理只恢复本机构失败任务，复用定时发送和有限重试。 */
    @Operation(
        summary = "重试失败的转码任务",
        description = "仅恢复本人教学空间失败任务，保留有限重试规则。"
    )
    @PostMapping("/files/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable String id) {
        mediaFileService.retryProcess(CurrentUser.companyId(), id);
        return ResponseEntity.noContent().build();
    }

    @Autowired
    private MediaFileService mediaFileService;

    /** 本机构查看文件；匿名或学员只能查看正式发布课程的图片封面，视频仍须机构身份。 */
    @Operation(
        summary = "取得媒资访问地址",
        description = "老师可查看本人媒资；公开访问仅限已发布课程封面，学习视频使用专用播放接口。"
    )
    @GetMapping("/files/{id}/content")
    public ResponseEntity<Void> content(@PathVariable String id, Authentication authentication)
        throws Exception {
        String url = mediaFileService.contentUrl(id, authentication);
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    /** 核对机构、处理状态及可用对象，向内容服务返回不含存储路径的元数据。 */
    @Operation(
        summary = "查询媒资绑定元数据",
        description = "核对教学空间、处理状态和对象可用性，不返回内部存储路径。"
    )
    @GetMapping("/files/{id}/binding-info")
    public ResponseEntity<Map<String, Object>> bindingInfo(@PathVariable String id)
        throws Exception {
        Map<String, Object> info = mediaFileService.bindingInfo(CurrentUser.companyId(), id);
        return info == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(info);
    }

    /**
     * 接收分页参数和可选媒资筛选条件，返回当前登录机构下的文件列表。
     */
    @Operation(
        summary = "媒资列表查询",
        description = "分页查询媒资文件列表，支持按文件名、文件类型等条件筛选"
    )
    @PostMapping("/files")
    public PageResult<MediaFiles> list(
        PageParams pageParams,
        @RequestBody(required = false) QueryMediaParamsDto queryMediaParamsDto
    ) {
        return mediaFileService.queryMediaFiels(
            CurrentUser.companyId(),
            pageParams,
            queryMediaParamsDto
        );
    }

    /**
     * 接收名为 filedata 的 multipart 文件，构造上传参数并返回保存后的媒资信息。
     */
    @Operation(
        summary = "上传媒资文件",
        description = "上传本人教学空间的课程媒资到兼容 S3 的对象存储（pgsty/silo）。"
    )
    @PostMapping(value = "/upload/coursefile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadFileResultDto upload(@RequestPart("filedata") MultipartFile filedata) {
        UploadFileParamsDto params = new UploadFileParamsDto();

        params.setFilename(filedata.getOriginalFilename());

        params.setFileSize(filedata.getSize());

        params.setFileType(
            filedata.getContentType() != null && filedata.getContentType().startsWith("video/")
                ? FILE_VIDEO
                : FILE_IMAGE
        );

        return mediaFileService.uploadFiles(CurrentUser.companyId(), filedata, params);
    }

    /** 控制器只调用播放业务；资格与对象查询在服务层，不缓存签名地址。 */
    @Operation(
        summary = "取得课程小节播放地址",
        description = "仅学员本人具有有效学习资格时签发短期播放地址，响应不缓存。"
    )
    @GetMapping("/playback/{courseId}/{lessonId}")
    public ResponseEntity<PlaybackUrlDto> playback(
        @PathVariable long courseId,
        @PathVariable long lessonId
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(mediaFileService.playback(courseId, lessonId));
    }

    /** 匿名入口只签发Learning核准的试学小节，不能指定任意媒资。 */
    @Operation(summary = "取得已发布试学小节视频地址")
    @GetMapping("/trial/{courseId}/{lessonId}")
    public ResponseEntity<PlaybackUrlDto> trial(
        @PathVariable long courseId,
        @PathVariable long lessonId
    ) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(mediaFileService.trialPlayback(courseId, lessonId));
    }
}
