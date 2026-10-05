package com.library.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 登录失败限流：同一个「账号 + IP」在滑动窗口内连续失败达到阈值后暂时锁定，
 * 防止暴力试密码。验证码那边本来就有 5 次作废 + 60 秒重发限制，这里补齐登录入口。
 *
 * <p>内存实现，单实例部署够用；重启后计数清零（攻击者重启也占不到便宜，
 * 因为窗口很短）。将来多实例部署时换成 Redis 即可，接口不变。
 */
@Component
public class LoginRateLimiter {

    /** 窗口内允许的失败次数上限。 */
    static final int MAX_FAILURES = 5;
    /** 滑动窗口长度（毫秒）：15 分钟。 */
    static final long WINDOW_MS = 15 * 60 * 1000L;
    /** 达到上限后的锁定时长（毫秒）：同样是 15 分钟，窗口滑出后自动解锁。 */
    static final long LOCK_MS = 15 * 60 * 1000L;

    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** 这个「账号 + IP」当前是否被锁定。 */
    public boolean isBlocked(String account, String ip, long now) {
        Deque<Long> deque = failures.get(key(account, ip));
        if (deque == null) {
            return false;
        }
        synchronized (deque) {
            evictExpired(deque, now);
            if (deque.isEmpty()) {
                failures.remove(key(account, ip));
                return false;
            }
            // 最近一次失败距今还没到锁定时长，且失败次数已满 → 锁定中
            return deque.size() >= MAX_FAILURES
                    && now - deque.peekLast() < LOCK_MS;
        }
    }

    /** 记录一次登录失败。 */
    public void recordFailure(String account, String ip, long now) {
        failures.compute(key(account, ip), (k, deque) -> {
            if (deque == null) {
                deque = new ArrayDeque<>();
            }
            synchronized (deque) {
                evictExpired(deque, now);
                deque.addLast(now);
            }
            return deque;
        });
    }

    /** 登录成功后清除该「账号 + IP」的失败记录。 */
    public void reset(String account, String ip) {
        failures.remove(key(account, ip));
    }

    /** 账号统一小写（登录账号不区分大小写），拼上 IP 作为限流维度。 */
    private static String key(String account, String ip) {
        String a = account == null ? "" : account.trim().toLowerCase();
        String i = ip == null ? "" : ip.trim();
        return a + "|" + i;
    }

    /** 滑出窗口的旧失败记录不再计数。 */
    private static void evictExpired(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
            deque.pollFirst();
        }
    }
}
