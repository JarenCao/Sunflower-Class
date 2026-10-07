package com.sunflower_class.base.exception;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 集中处理业务异常、未分类异常与请求校验错误，生成统一错误响应。
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 保留框架明确给出的 HTTP 状态，已下架课程应返回 404，不能被兜底异常改成 500。 */
    @ResponseBody
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RestErrorResponse> responseStatusException(
        ResponseStatusException error
    ) {
        return ResponseEntity.status(error.getStatusCode()).body(
            new RestErrorResponse(error.getReason())
        );
    }

    /**
     * 捕获业务异常并返回其中的提示信息；当前响应 HTTP 状态为 500。
     */
    @ResponseBody
    @ExceptionHandler(GlobalException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RestErrorResponse customException(GlobalException e) {
        log.error("【系统异常】错误信息：{}", e.getErrMessage(), e);

        return new RestErrorResponse(e.getErrMessage());
    }

    /**
     * 记录未分类异常并生成统一错误响应，避免各个接口单独处理同类错误。
     */
    @ResponseBody
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public RestErrorResponse exception(Exception e) {
        log.error("【系统异常】{}", e.getMessage(), e);

        return new RestErrorResponse(CommonError.UNKNOWN_ERROR.getMessage());
    }

    /**
     * 汇总请求参数校验失败的字段提示，转换为统一错误响应。
     */
    @ResponseBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RestErrorResponse methodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String errMessage = bindingResult
            .getAllErrors()
            .stream()
            // 从每个校验错误中提取默认提示，随后用逗号合并。
            .map(item -> item.getDefaultMessage())
            .collect(Collectors.joining(","));

        log.error("【系统异常】{}", errMessage);
        return new RestErrorResponse(errMessage);
    }
}
