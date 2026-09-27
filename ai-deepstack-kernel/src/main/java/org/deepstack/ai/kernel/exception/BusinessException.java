package org.deepstack.ai.kernel.exception;

import org.deepstack.ai.kernel.enums.common.AbstractErrorEnum;
import org.deepstack.ai.kernel.enums.common.CommonErrorCode;
import lombok.Getter;

/**
 * 业务异常 / Business exception（带错误码与对外消息）。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;
    private final String msg;

    /**
     * @param code 错误码
     * @param msg  对外消息
     */
    public BusinessException(Integer code, String msg) {
        super("business exception, code:" + code + ", msg:" + msg);
        this.code = code;
        this.msg = msg;
    }

    /**
     * 按错误枚举构造。
     */
    public BusinessException(AbstractErrorEnum commEnum) {
        super("business exception, code:" + commEnum.getCode() + ", msg:" + commEnum.getMessage());
        this.code = commEnum.getCode();
        this.msg = commEnum.getMessage();
    }

    /**
     * 抛出枚举对应的业务异常。
     */
    public static void of(AbstractErrorEnum commEnum) {
        throw new BusinessException(commEnum);
    }

    /**
     * 抛出指定码与消息的业务异常。
     */
    public static void of(Integer code, String msg) {
        throw new BusinessException(code, msg);
    }

    /**
     * 抛出通用 ERROR 码 + 自定义消息。
     */
    public static void of(String msg) {
        throw new BusinessException(CommonErrorCode.ERROR.getCode(), msg);
    }

    /**
     * 优先返回业务 msg。
     */
    @Override
    public String getMessage() {
        return msg != null ? msg : super.getMessage();
    }
}
