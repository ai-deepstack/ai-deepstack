package org.deepstack.ai.kernel.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * 从请求解析 Bearer Token。
 */
public final class BearerTokens {

    private BearerTokens() {
    }

    /**
     * @param request HTTP 请求
     * @return Bearer token；无则 null
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            return null;
        }
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
