package com.sunflower_class.base.exception;

/**
 * 通用错误枚举
 * 定义系统中常见的错误类型及其对应的错误信息
 */
public enum CommonError {
    
    UNKNOWN_ERROR("执行过程异常，请重试。"),
    PARAMS_ERROR("非法参数"),
    OBJECT_NULL("对象为空"),
    QUERY_NULL("查询结果为空"),
    REQUEST_NULL("请求参数为空");
    
    /**
     * 错误描述信息
     */
    private final String message;
    
    /**
     * 构造函数
     * @param message 错误描述信息
     */
    private CommonError(String message) {
        this.message = message;
    }
    
    /**
     * 获取错误描述信息
     * @return 错误描述信息
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * 根据枚举名称获取错误信息（忽略大小写）
     * @param name 枚举名称
     * @return 错误信息，如果未找到则返回null
     */
    public static String getMessageByName(String name) {
        try {
            return CommonError.valueOf(name.toUpperCase()).getMessage();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}