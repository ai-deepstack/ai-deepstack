package org.deepstack.ai.app.model.dto.request;

import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {

    /** 登录名 */
    private String username;

    /** 明文密码 */
    private String password;
}
