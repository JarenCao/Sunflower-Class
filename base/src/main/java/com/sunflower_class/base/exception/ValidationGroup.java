package com.sunflower_class.base.exception;

/**
 * 定义新增、修改、删除三类参数校验分组，供 DTO 校验注解使用。
 */
public class ValidationGroup {

    /**
     * 新增操作的参数校验分组标记。
     */
    public interface insert {
    }

    /**
     * 修改操作的参数校验分组标记。
     */
    public interface update {
    }

    /**
     * 删除操作的参数校验分组标记。
     */
    public interface delete {
    }
}
