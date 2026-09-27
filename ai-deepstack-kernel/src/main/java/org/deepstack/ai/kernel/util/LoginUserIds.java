package org.deepstack.ai.kernel.util;

import org.springframework.util.StringUtils;

/**
 * 登录用户 id 解析：ChatRequest.userId 等处存的是字符串形式的 AiUser.id。
 */
public final class LoginUserIds {

    private LoginUserIds() {
    }

    /**
     * 解析登录用户主键。
     *
     * @param raw 原始字符串，可为 null/空白
     * @return Long；无法解析返回 null
     */
    public static Long parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
