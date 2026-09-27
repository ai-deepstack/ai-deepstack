package org.deepstack.ai.app.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析服务（HS256）。不在日志中输出 token / secret。
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long expireHours;

    public JwtService(
            @Value("${deepstack.auth.jwt-secret}") String secret,
            @Value("${deepstack.auth.jwt-expire-hours:72}") long expireHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 requires >= 256-bit key; pad for short dev secrets
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
            for (int i = bytes.length; i < 32; i++) {
                padded[i] = (byte) i;
            }
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireHours = expireHours;
        log.info("JwtService 初始化完成, expireHours={}", expireHours);
    }

    /**
     * 为指定用户签发 JWT。
     *
     * @param userId   用户主键
     * @param username 登录名（写入 claim；日志仅记录 username/userId）
     * @return 已签名的 JWT 字符串
     */
    public String createToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plus(expireHours, ChronoUnit.HOURS);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        log.info("签发 JWT: userId={}, username={}", userId, username);
        return token;
    }

    /**
     * 校验并解析 JWT，返回 subject 中的用户 ID。
     *
     * @param token JWT 字符串（不记录 token 原文）
     * @return 用户主键
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long userId = Long.parseLong(claims.getSubject());
        String username = claims.get("username", String.class);
        log.debug("解析 JWT 成功: userId={}, username={}", userId, username);
        return userId;
    }
}
