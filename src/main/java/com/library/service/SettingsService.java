package com.library.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 系统设置：键值对存数据库 settings 表，内存缓存 30 秒减少查询。
 * 与 Python 版 get_settings()/save_setting() 行为一致：
 * 读取时以代码里的默认值兜底，数据库里有值就用数据库的。
 */
@Service
public class SettingsService {

    /** 默认设置项（首次启动写入数据库；之后以数据库内容为准）。 */
    public static final Map<String, String> DEFAULT_SETTINGS = Map.ofEntries(
            Map.entry("library_name", "我的图书室"),
            Map.entry("admin_account", "admin"),
            Map.entry("borrow_days", "30"),
            Map.entry("max_books_per_reader", "5"),
            Map.entry("max_renew_times", "1"),
            Map.entry("fine_per_day", "0.5"),
            // 1 = 允许读者在网上自己借书；0 = 只有管理员能办借书
            Map.entry("allow_self_borrow", "1"),
            // 1 = 读者在线借书要排队申请，由管理员办理借出；0 = 读者点一下就借出
            Map.entry("borrow_need_approval", "1"),
            // 借阅申请超过这么多天没办理，自动取消（0 = 不自动取消）
            Map.entry("request_expire_days", "3"),
            // 1 = 允许读者自己上网注册办卡；0 = 必须由管理员先登记
            Map.entry("reader_self_register", "1"),
            // 1 = 自助注册的读者要管理员确认后才能借书；0 = 注册完就能借
            Map.entry("reader_register_need_approval", "0"),
            // 发件邮箱（验证码 / 到期提醒）；不配就由管理员转告
            Map.entry("smtp_host", ""),
            Map.entry("smtp_port", "465"),
            Map.entry("smtp_user", ""),
            Map.entry("smtp_pass", ""),
            // 到期提醒：1 = 系统自动给读者发提醒邮件
            Map.entry("reminder_enabled", "1"),
            Map.entry("reminder_days_before", "3"),
            // 预约到馆时要不要发邮件通知（0 = 只在读者端显示提示）
            Map.entry("mail_notify_ready", "1"),
            // 公告（读者端顶部 + 公开的公告与规则页）
            Map.entry("announcement", ""),
            // 收款方式说明 + 微信/支付宝收款码图片文件名
            Map.entry("pay_note", ""),
            Map.entry("pay_qr_wechat", ""),
            Map.entry("pay_qr_alipay", ""),
            // 1 = 同一浏览器允许管理端和读者端同时登录；0 = 后登的顶掉先登的
            Map.entry("allow_multi_login", "0"),
            // 每日自动备份
            Map.entry("auto_backup_enabled", "1"),
            Map.entry("auto_backup_keep_days", "30"));

    private final JdbcTemplate jdbc;
    private volatile Map<String, String> cache;
    private volatile long cacheAt = 0;
    private static final long CACHE_TTL_MS = 30_000;

    public SettingsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 读取全部设置（带 30 秒缓存）。 */
    public Map<String, String> all() {
        long now = System.currentTimeMillis();
        Map<String, String> snapshot = cache;
        if (snapshot != null && now - cacheAt < CACHE_TTL_MS) {
            return snapshot;
        }
        synchronized (this) {
            if (cache != null && System.currentTimeMillis() - cacheAt < CACHE_TTL_MS) {
                return cache;
            }
            Map<String, String> conf = new ConcurrentHashMap<>(DEFAULT_SETTINGS);
            jdbc.query("SELECT `key`, `value` FROM settings", rs -> {
                String value = rs.getString("value");
                if (value != null) {
                    conf.put(rs.getString("key"), value);
                }
            });
            cache = conf;
            cacheAt = System.currentTimeMillis();
            return conf;
        }
    }

    public String get(String key) {
        return all().get(key);
    }

    /** 保存一项设置（写入数据库并立刻刷新缓存）。 */
    public void save(String key, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        jdbc.update("INSERT INTO settings(`key`, `value`) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)", key, text);
        evict();
    }

    public void evict() {
        cache = null;
    }

    // ---- 类型化读取（解析失败回退默认值，与 Python 版 conf_int/conf_float 一致）----

    public int getInt(String key) {
        return getInt(key, Integer.parseInt(DEFAULT_SETTINGS.get(key)));
    }

    public int getInt(String key, int fallback) {
        try {
            return (int) Float.parseFloat(all().get(key));
        } catch (Exception e) {
            return fallback;
        }
    }

    public double getDouble(String key) {
        return getDouble(key, Double.parseDouble(DEFAULT_SETTINGS.get(key)));
    }

    public double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(all().get(key));
        } catch (Exception e) {
            return fallback;
        }
    }

    public boolean getBool(String key) {
        return "1".equals(all().get(key));
    }

    public String getAdminPasswordHash() {
        return all().getOrDefault("admin_password_hash", "");
    }
}
