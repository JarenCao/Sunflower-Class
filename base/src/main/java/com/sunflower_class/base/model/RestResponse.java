package com.sunflower_class.base.model;

import lombok.Data;

/**
 * 统一业务响应结构：code 为业务码，msg 为提示，result 为返回数据。
 */
@Data
public class RestResponse<T> {

    private int code;
    private String msg;

    private T result;

    /**
     * 构造业务响应；无参时使用成功码 0，其他重载接收状态码、提示及可选结果。
     */
    public RestResponse() {
        this(0, "success");
    }

    /**
     * 构造业务响应；无参时使用成功码 0，其他重载接收状态码、提示及可选结果。
     */
    public RestResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    /**
     * 构造业务响应；无参时使用成功码 0，其他重载接收状态码、提示及可选结果。
     */
    public RestResponse(int code, String msg, T result) {
        this.code = code;
        this.msg = msg;
        this.result = result;
    }

    /**
     * 构造业务成功响应，状态码为 0；带参重载将传入值放入 result。
     */
    public static <T> RestResponse<T> success() {
        return new RestResponse<>(0, "success");
    }

    /**
     * 构造业务成功响应，状态码为 0；带参重载将传入值放入 result。
     */
    public static <T> RestResponse<T> success(T msg) {
        return new RestResponse<>(0, "success", msg);
    }

    /**
     * 构造业务失败响应；只传提示时使用错误码 1，也可由调用方指定错误码。
     */
    public static <T> RestResponse<T> error(String msg) {
        return new RestResponse<>(1, msg);
    }

    /**
     * 构造业务失败响应；只传提示时使用错误码 1，也可由调用方指定错误码。
     */
    public static <T> RestResponse<T> error(int code, String msg) {
        return new RestResponse<>(code, msg);
    }

    /**
     * 设置业务结果并返回当前响应对象，支持链式构造。
     */
    public RestResponse<T> setResult(T result) {
        this.result = result;
        return this;
    }
}
