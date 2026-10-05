package com.library.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final LoginRateLimiter limiter = new LoginRateLimiter();
    private static final long T0 = 1_700_000_000_000L;

    @Test
    void 连续失败五次后锁定() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            assertFalse(limiter.isBlocked("admin", "1.2.3.4", T0));
            limiter.recordFailure("admin", "1.2.3.4", T0 + i);
        }
        assertTrue(limiter.isBlocked("admin", "1.2.3.4", T0 + 1000));
    }

    @Test
    void 锁定期间无论对错都拒绝_直到窗口滑出() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("admin", "1.2.3.4", T0);
        }
        // 锁定 15 分钟内：仍被锁
        assertTrue(limiter.isBlocked("admin", "1.2.3.4", T0 + 14 * 60_000));
        // 超过锁定时长：旧记录滑出窗口，自动解锁
        assertFalse(limiter.isBlocked("admin", "1.2.3.4", T0 + LoginRateLimiter.WINDOW_MS + 1));
    }

    @Test
    void 登录成功清除失败记录() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("admin", "1.2.3.4", T0);
        }
        limiter.reset("admin", "1.2.3.4");
        // 清除后再失败一次也还没到阈值
        limiter.recordFailure("admin", "1.2.3.4", T0);
        assertFalse(limiter.isBlocked("admin", "1.2.3.4", T0));
    }

    @Test
    void 不同账号和不同IP互不影响() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES; i++) {
            limiter.recordFailure("admin", "1.2.3.4", T0);
        }
        // 换账号
        assertFalse(limiter.isBlocked("reader", "1.2.3.4", T0));
        // 换 IP
        assertFalse(limiter.isBlocked("admin", "5.6.7.8", T0));
        // 账号大小写视为同一个
        assertTrue(limiter.isBlocked("Admin ", "1.2.3.4", T0));
    }

    @Test
    void 旧失败记录滑出窗口后_新失败从头计数() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES - 1; i++) {
            limiter.recordFailure("admin", "1.2.3.4", T0);
        }
        // 窗口滑出后（旧的 4 次都不算数了）再失败 1 次 → 未达阈值
        long later = T0 + LoginRateLimiter.WINDOW_MS + 1;
        limiter.recordFailure("admin", "1.2.3.4", later);
        assertFalse(limiter.isBlocked("admin", "1.2.3.4", later));
    }
}
