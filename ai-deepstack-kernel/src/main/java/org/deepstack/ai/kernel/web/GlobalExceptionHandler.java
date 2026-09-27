package org.deepstack.ai.kernel.web;

import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：业务异常与未捕获异常统一转为 {@link Response}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常 → 失败响应（记录 warn）。
     */
    @ExceptionHandler(BusinessException.class)
    public Response<Void> handleBusiness(BusinessException e) {
        log.warn("Business error: code={}, msg={}", e.getCode(), e.getMessage());
        return Response.fail(e);
    }

    /**
     * 未处理异常 → 系统错误响应（记录 error）。
     */
    @ExceptionHandler(Exception.class)
    public Response<Void> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return Response.fail(CommonErrorCode.SYSTEM_ERROR.getCode(), e.getMessage());
    }
}
