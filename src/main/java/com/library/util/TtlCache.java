package com.library.util;

import java.util.function.Supplier;

/**
 * 极简 TTL 缓存：单值缓存（key 由持有者隐含），过期后由下一个读请求重算。
 * 线程安全：synchronized 保护；并发重算最多发生一次浪费，不影响正确性。
 *
 * <p>用途：统计报表（60 秒）、数据分析（120 秒）、图书推荐（300 秒）这类
 * “多组聚合查询、结果容忍片刻旧数据”的页面 —— 用一点点旧换掉大量重复计算。
 * 单实例部署下进程内缓存效果与 Redis 一致；将来多实例部署时再换 Redis。
 */
public class TtlCache<T> {

    private final long ttlMillis;
    private volatile T value;
    private volatile long cachedAt;

    public TtlCache(long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000L;
    }

    /** 有缓存且未过期就返回缓存，否则计算一次并缓存。 */
    public T getOrCompute(Supplier<T> producer) {
        long now = System.currentTimeMillis();
        T snapshot = value;
        if (snapshot != null && now - cachedAt < ttlMillis) {
            return snapshot;
        }
        synchronized (this) {
            if (value != null && System.currentTimeMillis() - cachedAt < ttlMillis) {
                return value;
            }
            value = producer.get();
            cachedAt = System.currentTimeMillis();
            return value;
        }
    }

    /** 数据变动后主动失效（例如新增借阅后清报表缓存）。 */
    public void evict() {
        synchronized (this) {
            value = null;
            cachedAt = 0;
        }
    }
}
