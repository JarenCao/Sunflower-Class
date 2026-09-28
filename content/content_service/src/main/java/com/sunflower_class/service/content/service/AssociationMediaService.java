package com.sunflower_class.service.content.service;

import com.sunflower_class.model.dto.BindTeachplanMediaDto;

/**
 * 教学计划与媒资关联的业务契约。
 */
public interface AssociationMediaService {
    /**
     * 校验课程归属、媒资文件及可用状态，再以事务替换教学计划的旧绑定。
     */
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto);
}
