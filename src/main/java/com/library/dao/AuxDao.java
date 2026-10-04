package com.library.dao;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.library.entity.OperationLog;
import com.library.entity.PasswordReset;
import com.library.entity.ReminderLog;
import com.library.util.DateUtil;

/** 日志 / 密码重置 / 提醒记录三张表的简单查询，合并放在一个 DAO（都是低频表）。 */
@Repository
public class AuxDao {

    private static final BeanPropertyRowMapper<OperationLog> LOG_MAPPER =
            new BeanPropertyRowMapper<>(OperationLog.class);
    private static final BeanPropertyRowMapper<PasswordReset> RESET_MAPPER =
            new BeanPropertyRowMapper<>(PasswordReset.class);
    private static final BeanPropertyRowMapper<ReminderLog> REMINDER_MAPPER =
            new BeanPropertyRowMapper<>(ReminderLog.class);

    private final JdbcTemplate jdbc;

    public AuxDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- 操作日志 ----------------

    public List<OperationLog> listLogs(String q, String role) {
        List<String> where = new java.util.ArrayList<>();
        List<Object> params = new java.util.ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.add("(action LIKE ? OR target LIKE ? OR detail LIKE ? OR who LIKE ?)");
            for (int i = 0; i < 4; i++) {
                params.add("%" + q.trim() + "%");
            }
        }
        if (role != null && !role.isBlank()) {
            where.add("role = ?");
            params.add(role.trim());
        }
        String sqlWhere = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        return jdbc.query("SELECT * FROM operation_logs" + sqlWhere + " ORDER BY id DESC LIMIT 300",
                LOG_MAPPER, params.toArray());
    }

    // ---------------- 密码重置 ----------------

    public PasswordReset latestReset(int readerId) {
        List<PasswordReset> rows = jdbc.query(
                "SELECT * FROM password_resets WHERE reader_id = ? ORDER BY id DESC LIMIT 1",
                RESET_MAPPER, readerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public PasswordReset latestUnusedReset(int readerId) {
        List<PasswordReset> rows = jdbc.query(
                "SELECT * FROM password_resets WHERE reader_id = ? AND used = 0 ORDER BY id DESC LIMIT 1",
                RESET_MAPPER, readerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertReset(int readerId, String code, String expireAt) {
        jdbc.update("INSERT INTO password_resets(reader_id, code, expire_at, created_at) VALUES (?,?,?,?)",
                readerId, code, expireAt, DateUtil.nowStr());
    }

    /** 作废该读者全部未用验证码（同一读者只保留最新一个）。 */
    public void invalidateResets(int readerId) {
        jdbc.update("UPDATE password_resets SET used = 1 WHERE reader_id = ? AND used = 0", readerId);
    }

    public void markResetUsed(int readerId) {
        jdbc.update("UPDATE password_resets SET used = 1 WHERE reader_id = ?", readerId);
    }

    public void incrementResetAttempts(int resetId) {
        jdbc.update("UPDATE password_resets SET attempts = attempts + 1 WHERE id = ?", resetId);
    }

    /** 工作台徽标：未用且未过期的验证码数。 */
    public int pendingResetCount() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM password_resets WHERE used = 0 AND expire_at > ?",
                Integer.class, DateUtil.nowStr());
        return n == null ? 0 : n;
    }

    /** 近 2 小时内申请重置密码的记录（工作台提醒列表）。 */
    public List<PasswordReset> recentResets() {
        String since = java.time.LocalDateTime.now().minusHours(2).format(DateUtil.DATETIME);
        return jdbc.query("SELECT pr.*, r.name AS reader_name, r.card_no, r.phone "
                        + "FROM password_resets pr JOIN readers r ON r.id = pr.reader_id "
                        + "WHERE pr.used = 0 AND pr.created_at > ? ORDER BY pr.id DESC",
                RESET_MAPPER, since);
    }

    // ---------------- 提醒记录 ----------------

    public void insertReminder(int borrowId, String kind, String to, boolean ok, String message) {
        jdbc.update("INSERT INTO reminder_logs(borrow_id, kind, sent_to, ok, message, sent_at) "
                        + "VALUES (?,?,?,?,?,?)", borrowId, kind, to == null ? "" : to, ok ? 1 : 0,
                message == null ? "" : (message.length() > 255 ? message.substring(0, 255) : message),
                DateUtil.nowStr());
    }

    /** 同一本书同一种提醒最近 20 小时内是否发过（防重复轰炸）。 */
    public boolean reminderSentRecently(int borrowId, String kind, int hours) {
        String last = jdbc.query("SELECT sent_at FROM reminder_logs WHERE borrow_id = ? AND kind = ? "
                        + "AND ok = 1 ORDER BY id DESC LIMIT 1", rs -> rs.next() ? rs.getString(1) : null,
                borrowId, kind);
        if (last == null) {
            return false;
        }
        java.time.LocalDateTime sentAt = DateUtil.parseDateTime(last);
        return sentAt != null && sentAt.isAfter(java.time.LocalDateTime.now().minusHours(hours));
    }

    public List<ReminderLog> listReminders() {
        return jdbc.query("SELECT rl.*, b.title AS book_title, r.name AS reader_name, br.due_date "
                + "FROM reminder_logs rl LEFT JOIN borrow_records br ON br.id = rl.borrow_id "
                + "LEFT JOIN books b ON b.id = br.book_id LEFT JOIN readers r ON r.id = br.reader_id "
                + "ORDER BY rl.id DESC LIMIT 200", REMINDER_MAPPER);
    }
}
