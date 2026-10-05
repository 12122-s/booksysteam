package com.library.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtUtilTest {

    private static final String SECRET_48 = "library-jwt-dev-secret-change-me-please-0123456789";

    @Test
    void 签发后能解析出全部载荷() {
        JwtUtil jwt = new JwtUtil(SECRET_48, 120);
        String token = jwt.create("R260015", "reader", "测试读者", 42);

        Claims claims = jwt.parse(token);
        assertEquals("R260015", claims.getSubject());
        assertEquals("reader", claims.get("role", String.class));
        assertEquals("测试读者", claims.get("name", String.class));
        assertEquals(42, claims.get("readerId", Integer.class));
    }

    @Test
    void 管理员载荷没有读者id() {
        JwtUtil jwt = new JwtUtil(SECRET_48, 120);
        Claims claims = jwt.parse(jwt.create("admin", "admin", "管理员", null));
        assertEquals("admin", claims.getSubject());
        assertEquals("admin", claims.get("role", String.class));
        assertEquals(null, claims.get("readerId"));
    }

    @Test
    void 篡改载荷_签名校验拒绝() {
        JwtUtil jwt = new JwtUtil(SECRET_48, 120);
        JwtUtil other = new JwtUtil(SECRET_48.substring(0, 32) + "different", 120);
        String forged = other.create("admin", "admin", "伪造", null);

        assertThrows(JwtException.class, () -> jwt.parse(forged));
        // isSignedByUs 只判签名，伪造 token 不认
        assertEquals(false, jwt.isSignedByUs(forged));
    }

    @Test
    void 过期token_解析抛ExpiredJwtException() {
        // TTL 用一个负的小时数让 token 立即过期
        JwtUtil jwt = new JwtUtil(SECRET_48, -1);
        String token = jwt.create("admin", "admin", "管理员", null);

        assertThrows(ExpiredJwtException.class, () -> jwt.parse(token));
        // 签名本身是对的（isSignedByUs 把过期当“非签名问题”）
        assertTrue(jwt.isSignedByUs(token));
    }

    @Test
    void 垃圾字符串_解析抛异常() {
        JwtUtil jwt = new JwtUtil(SECRET_48, 120);
        assertThrows(JwtException.class, () -> jwt.parse("not-a-jwt"));
        assertThrows(IllegalArgumentException.class, () -> jwt.parse(""));
    }
}
