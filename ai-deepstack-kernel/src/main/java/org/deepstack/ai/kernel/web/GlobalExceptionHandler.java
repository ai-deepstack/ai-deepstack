package org.deepstack.ai.kernel.web;

import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.model.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
     * 上传超过 multipart 限制 → 明确提示上限，避免落到通用 500。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Response<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        long maxBytes = e.getMaxUploadSize();
        String limit = maxBytes > 0
                ? (maxBytes / (1024 * 1024)) + "MB"
                : "配置上限";
        log.warn("Upload size exceeded: max={}", limit);
        return Response.fail(CommonErrorCode.INVALID_PARAM.getCode(),
                "上传文件过大，单文件最大 " + limit);
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
