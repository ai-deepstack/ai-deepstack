package org.deepstack.ai.app.model.dto.response;

import lombok.Data;

/**
 * 登录成功响应。
 */
@Data
public class LoginResponse {

    /** JWT */
    private String token;

    /** 用户主键 */
    private Long userId;

    /** 显示名 */
    private String displayName;

    /** 是否管理员：1是 0否 */
    private Integer isAdmin;
}
