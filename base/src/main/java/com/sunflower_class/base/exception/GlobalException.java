package com.sunflower_class.base.exception;

/**
 * 全局业务异常
 * 用于抛出业务层面的异常，由全局异常处理器统一处理
 * 
 * @author yourname
 * @date 2026-08-11
 */
public class GlobalException extends RuntimeException {
    
    private String errMessage;
    
    /**
     * 无参构造方法
     */
    public GlobalException() {
        super();
    }
    
    /**
     * 带错误信息的构造方法
     * @param errMessage 错误信息
     */
    public GlobalException(String errMessage) {
        super(errMessage);
        this.errMessage = errMessage;
    }
    
    /**
     * 获取错误信息
     * @return 错误信息
     */
    public String getErrMessage() {
        return errMessage;
    }
    
    /**
     * 静态方法：快速抛出异常（使用 CommonError）
     * @param commonError 通用错误枚举
     */
    public static void cast(CommonError commonError) {
        throw new GlobalException(commonError.getMessage());
    }
    
    /**
     * 静态方法：快速抛出异常（使用错误信息）
     * @param errMessage 错误信息
     */
    public static void cast(String errMessage) {
        throw new GlobalException(errMessage);
    }
}