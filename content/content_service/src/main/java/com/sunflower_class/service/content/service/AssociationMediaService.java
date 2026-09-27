package com.sunflower_class.service.content.service;

import com.sunflower_class.model.dto.BindTeachplanMediaDto;

/**
 * 教学计划与媒资关联的业务契约。
 */
public interface AssociationMediaService {
    /**
     * 检查教学计划存在后，在事务中删除旧媒资关联并写入新关联；当前未校验媒资可用性和登录机构。
     */
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto);
}
