package com.sunflower_class.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Elasticsearch 文档：规范化检索字段与原始正式快照分开，事件编号是写入版本。 */
@Schema(description = "Elasticsearch 文档：规范化检索字段与原始正式快照分开，事件编号是写入版本。")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseSearchDocument {

    /** 课程编号，用作 Elasticsearch 文档标识。 */
    @Schema(description = "课程编号，用作 Elasticsearch 文档标识。")
    private long id;

    /** 事件编号，用于版本控制和重复消息去重。 */
    @Schema(description = "事件编号，用于版本控制和重复消息去重。")
    private long eventId;

    /** 课程发布状态：30501 未发布、30502 已发布、30503 已下架。 */
    @Schema(description = "课程发布状态：30501 未发布、30502 已发布、30503 已下架。")
    private String status;

    /** 规范化的课程检索名称。 */
    @Schema(description = "规范化的课程检索名称。")
    private String searchName;

    /** 规范化的课程检索标签。 */
    @Schema(description = "规范化的课程检索标签。")
    private String searchTags;

    /** 课程分类编号。 */
    @Schema(description = "课程分类编号。")
    private String category;

    /** 原业务快照，字段结构与消息协议保持一致。 */
    @Schema(description = "原业务快照，字段结构与消息协议保持一致。")
    private Map<String, Object> snapshot;
}
