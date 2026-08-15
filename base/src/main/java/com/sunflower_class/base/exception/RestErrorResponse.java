package com.sunflower_class.base.exception;

import java.io.Serializable;

/**
 * REST API 错误响应封装类
 * 用于统一返回错误信息
 * 
 * @author yourname
 * @date 2026-08-11
 */
public class RestErrorResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误描述信息
     */
    private String errMessage;
    
    /**
     * 无参构造方法（框架反序列化使用）
     */
    public RestErrorResponse() {
    }
    
    /**
     * 有参构造方法
     * @param errMessage 错误信息
     */
    public RestErrorResponse(String errMessage) {
        this.errMessage = errMessage;
    }
    
    public String getErrMessage() {
        return errMessage;
    }
    
    public void setErrMessage(String errMessage) {
        this.errMessage = errMessage;
    }
    
    @Override
    public String toString() {
        return "RestErrorResponse{errMessage='" + errMessage + "'}";
    }
}