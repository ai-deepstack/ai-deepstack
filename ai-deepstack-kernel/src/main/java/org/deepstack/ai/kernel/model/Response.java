package org.deepstack.ai.kernel.model;

import org.deepstack.ai.kernel.enums.common.AbstractErrorEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import org.deepstack.ai.kernel.exception.BusinessException;
import org.deepstack.ai.kernel.observability.MdcKeys;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;
import java.util.Objects;

/**
 * 统一 API 响应包装 / Unified API response wrapper.
 */
@Data
public class Response<T> implements Serializable {
    private Long cost;
    private Integer code;
    private String message;
    private String requestId;
    private T data;

    /**
     * 是否业务成功（code == SUCCESS）。
     */
    public boolean isSuccess() {
        return Objects.equals(this.code, CommonErrorCode.SUCCESS.getCode());
    }

    /**
     * 成功响应（无 data）。
     */
    public static <T> Response<T> success() {
        return success(null);
    }

    /**
     * 成功响应（带 data）。
     *
     * @param data 业务数据，可为 null
     */
    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setCode(200);
        response.setData(data);
        response.setMessage("SUCCESS");
        response.fillMeta();
        return response;
    }

    /**
     * 失败响应（无 data）。
     *
     * @param errorCode  错误码
     * @param errMessage 错误信息
     */
    public static <T> Response<T> fail(Integer errorCode, String errMessage) {
        return fail(errorCode, errMessage, null);
    }

    /**
     * 失败响应（带 data）。
     *
     * @param errorCode  错误码
     * @param errMessage 错误信息
     * @param data       可选附加数据
     */
    public static <T> Response<T> fail(Integer errorCode, String errMessage, T data) {
        Response<T> response = new Response<>();
        response.setCode(errorCode);
        response.setMessage(errMessage);
        response.setData(data);
        response.fillMeta();
        return response;
    }

    /**
     * 按错误枚举构造失败响应。
     */
    public static <T> Response<T> fail(AbstractErrorEnum commEnum) {
        return fail(commEnum.getCode(), commEnum.getMessage(), null);
    }

    /**
     * 按错误枚举构造失败响应（带 data）。
     */
    public static <T> Response<T> fail(AbstractErrorEnum commEnum, T data) {
        return fail(commEnum.getCode(), commEnum.getMessage(), data);
    }

    /**
     * 按业务异常构造失败响应。
     */
    public static <T> Response<T> fail(BusinessException e) {
        return fail(e.getCode(), e.getMessage(), null);
    }

    /**
     * 按业务异常构造失败响应（带 data）。
     */
    public static <T> Response<T> fail(BusinessException e, T data) {
        return fail(e.getCode(), e.getMessage(), data);
    }

    /**
     * 填充耗时与 requestId（来自 RequestInfo / MDC）。
     */
    private void fillMeta() {
        RequestInfo requestInfo = RequestInfo.get();
        this.cost = System.currentTimeMillis() - requestInfo.getStartTime();
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            this.requestId = traceId;
        }
    }
}
