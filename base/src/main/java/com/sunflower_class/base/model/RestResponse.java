package com.sunflower_class.base.model;

import lombok.Data;

@Data
public class RestResponse<T> {
    private int code;
    private String msg;

    private T result;

    public RestResponse() {
        this(0, "success");
    }

    public RestResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public RestResponse(int code, String msg, T result) {
        this.code = code;
        this.msg = msg;
        this.result = result;
    }

    public static <T> RestResponse<T> success() {
        return new RestResponse<>(0, "success");
    }

    public static <T> RestResponse<T> success(T msg) {
        return new RestResponse<>(0, "success", msg);
    }

    public static <T> RestResponse<T> error(String msg) {
        return new RestResponse<>(1, msg);
    }

    public static <T> RestResponse<T> error(int code, String msg) {
        return new RestResponse<>(code, msg);
    }

    public RestResponse<T> setResult(T result) {
        this.result = result;
        return this;
    }
}
