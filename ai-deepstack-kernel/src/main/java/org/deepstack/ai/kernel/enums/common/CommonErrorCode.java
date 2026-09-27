package org.deepstack.ai.kernel.enums.common;

import lombok.Getter;

@Getter
public enum CommonErrorCode implements AbstractErrorEnum {
    ERROR(0, "Default Error!"),
    SUCCESS(200, "Success"),
    SYSTEM_ERROR(500, "System Error!"),
    BAD_REQUEST(400, "Bad Request!"),
    NEED_LOGIN(401, "Unauthorized!"),
    UNAUTHORIZED(403, "Unauthorized!"),
    NOT_FOUND(404, "Not Found!"),
    CONFLICT(409, "Conflict!"),
    TOO_MANY_REQUESTS(429, "Too Many Requests!"),
    MISSING_PARAM(550, "Missing Param!"),
    INVALID_PARAM(551, "Invalid Param!");

    private final Integer code;
    private final String message;

    CommonErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
