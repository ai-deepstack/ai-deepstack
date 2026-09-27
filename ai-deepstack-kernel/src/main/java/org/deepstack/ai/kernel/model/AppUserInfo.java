package org.deepstack.ai.kernel.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserInfo extends BaseUserInfo {
    private String nickname;
    private String avatar;

    /** 是否管理员：1是 0否（与 ai_user.is_admin 对齐） */
    private Integer isAdmin;
}
