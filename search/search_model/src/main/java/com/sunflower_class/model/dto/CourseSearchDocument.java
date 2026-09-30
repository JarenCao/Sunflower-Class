package com.sunflower_class.model.dto;

import java.util.Map;

/** Elasticsearch 文档：规范化检索字段与原始正式快照分开，事件编号是写入版本。 */
public record CourseSearchDocument(
    long id,
    long eventId,
    String status,
    String searchName,
    String searchTags,
    String category,
    Map<String, Object> snapshot
) {}
