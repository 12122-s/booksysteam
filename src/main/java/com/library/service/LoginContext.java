package com.library.service;

import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.library.entity.Reader;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 当前登录身份上下文。
 *
 * <p>会话结构与 Python 版完全对齐（同一套前端行为）：
 * - 管理员：session["logged_in"] = true
 * - 读者：  session["reader_id"] / reader_name / reader_card
 * - CSRF：  session["_csrf"]
 * - 启动戳：session["_boot"]（系统重启后旧登录态作废）
 */
@Service
public class LoginContext {

    /** 当前身份（用于操作日志）。 */
    public record Identity(String who, String role) {
    }

    /** 取当前 HttpSession；不在请求上下文中（后台任务）返回 null。 */
    public HttpSession session() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest().getSession(false);
    }

    /** 是否管理员已登录。 */
    public boolean isAdmin() {
        HttpSession session = session();
        return session != null && Boolean.TRUE.equals(session.getAttribute("logged_in"));
    }

    /** 当前登录读者（会话里的 id）；未登录返回 null。 */
    public Integer readerId() {
        HttpSession session = session();
        return session == null ? null : (Integer) session.getAttribute("reader_id");
    }

    public String readerName() {
        HttpSession session = session();
        return session == null ? null : (String) session.getAttribute("reader_name");
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public LoginContext(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 当前登录读者实体（已注销返回 null）；后台任务中返回 null。 */
    public Reader currentReader() {
        Integer readerId = readerId();
        if (readerId == null) {
            return null;
        }
        var readers = jdbc.query(
                "SELECT * FROM readers WHERE id = ? AND is_deleted = 0",
                new org.springframework.jdbc.core.BeanPropertyRowMapper<>(Reader.class), readerId);
        return readers.isEmpty() ? null : readers.get(0);
    }

    /** 日志用：当前是谁在操作。 */
    public Identity identity() {
        if (isAdmin()) {
            return new Identity("管理员", "管理员");
        }
        Integer readerId = readerId();
        if (readerId != null) {
            String name = readerName();
            return new Identity(name == null ? "读者" : name, "读者");
        }
        return new Identity("访客", "访客");
    }

    /** 客户端 IP（优先 X-Forwarded-For，nginx 反代场景）。 */
    public String clientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
