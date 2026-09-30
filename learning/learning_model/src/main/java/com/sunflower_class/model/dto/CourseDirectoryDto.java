package com.sunflower_class.model.dto;

/** 学员公开目录响应；目录来自发布快照，不表示学习资格已经开通。 */
public record CourseDirectoryDto(long id, String teachplan) {}
