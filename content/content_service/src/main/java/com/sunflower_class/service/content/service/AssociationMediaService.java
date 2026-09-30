package com.sunflower_class.service.content.service;

import com.sunflower_class.model.dto.BindTeachplanMediaDto;
import java.util.Map;

/**
 * 教学计划与媒资关联的业务契约。
 */
public interface AssociationMediaService {
    /**
     * 校验课程归属、媒资文件及可用状态，再以事务替换教学计划的旧绑定。
     */
    public void associationMedia(BindTeachplanMediaDto bindTeachplanMediaDto);

    /** 发布前复用绑定校验：确认媒资属于当前机构、已处理完成且存储对象存在。 */
    Map<?, ?> requireReadyMedia(String mediaId);
}
