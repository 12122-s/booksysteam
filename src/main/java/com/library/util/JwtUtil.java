package com.library.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/**
 * JWT 签发与解析（HS256）。
 *
 * <p>载荷内容：sub = 身份标识（"admin" 或读者卡号），role = admin/reader，
 * name = 显示名，readerId = 读者 id（仅读者）。过期时间由 jwt-ttl-minutes 控制。
 *
 * <p>密钥来自 library.jwt-secret（环境变量 LIB_JWT_SECRET 覆盖），
 * HS256 要求至少 32 个字符。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtUtil(@Value("${library.jwt-secret}") String secret,
                   @Value("${library.jwt-ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    /** 签发一个 token。 */
    public String create(String subject, String role, String name, Integer readerId) {
        var builder = Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("name", name == null ? "" : name)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMinutes * 60_000));
        if (readerId != null) {
            builder.claim("readerId", readerId);
        }
        return builder.signWith(key).compact();
    }

    /**
     * 解析并校验签名与有效期；无效/过期/被篡改时抛
     * io.jsonwebtoken.JwtException（由 API 异常处理统一转成 401）。
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** 便捷判断：签名是不是本系统签的（不管有效期）。 */
    public boolean isSignedByUs(String token) {
        try {
            parse(token);
            return true;
        } catch (SignatureException e) {
            return false;
        } catch (Exception e) {
            return true;   // 过期等非签名问题，说明签名本身是对的
        }
    }
}
