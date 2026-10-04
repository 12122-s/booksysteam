package com.library.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.library.util.DateUtil;

/**
 * 操作日志：谁、什么时候、干了什么。
 * 日志写失败不能影响主流程，所以异常全部吞掉（与 Python 版 log_action 一致）。
 */
@Service
public class LogService {

    private final JdbcTemplate jdbc;
    private final LoginContext loginContext;

    public LogService(JdbcTemplate jdbc, LoginContext loginContext) {
        this.jdbc = jdbc;
        this.loginContext = loginContext;
    }

    /** 记一条日志；who/role 不传时自动取当前登录身份。 */
    public void action(String action, String target, String detail, String who, String role, String ip) {
        try {
            if (who == null) {
                LoginContext.Identity identity = loginContext.identity();
                who = identity.who();
                role = identity.role();
            }
            if (ip == null) {
                ip = loginContext.clientIp();
            }
            jdbc.update("INSERT INTO operation_logs(created_at, who, role, action, target, detail, ip) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    DateUtil.nowStr(), clip(who, 60), clip(role, 10), clip(action, 40),
                    clip(target, 120), clip(detail, 255), clip(ip, 40));
        } catch (Exception e) {
            // 日志失败不影响业务
        }
    }

    /** 常用重载：当前身份 + 当前请求 IP。 */
    public void action(String action) {
        action(action, "", "", null, null, null);
    }

    public void action(String action, String target) {
        action(action, target, "", null, null, null);
    }

    public void action(String action, String target, String detail) {
        action(action, target, detail, null, null, null);
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
