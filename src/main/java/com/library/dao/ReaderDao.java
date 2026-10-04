package com.library.dao;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.library.dao.BookDao.ListResult;
import com.library.entity.Reader;
import com.library.util.DateUtil;

/** 读者表数据访问。 */
@Repository
public class ReaderDao {

    private static final BeanPropertyRowMapper<Reader> MAPPER = new BeanPropertyRowMapper<>(Reader.class);

    private final JdbcTemplate jdbc;

    public ReaderDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 通用联查（办借书页的候选列表等场景）。 */
    public List<Reader> listAll(String sql, Object... params) {
        return jdbc.query(sql, MAPPER, params);
    }

    public Reader findById(int id) {
        List<Reader> rows = jdbc.query("SELECT * FROM readers WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Reader findByIdActive(int id) {
        List<Reader> rows = jdbc.query("SELECT * FROM readers WHERE id = ? AND is_deleted = 0",
                MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 卡号或手机号匹配（登录、找回密码共用）。 */
    public Reader findByAccount(String account) {
        List<Reader> rows = jdbc.query(
                "SELECT * FROM readers WHERE is_deleted = 0 AND (card_no = ? "
                        + "OR (COALESCE(phone,'') <> '' AND phone = ?)) LIMIT 1",
                MAPPER, account, account);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Reader findByCardNo(String cardNo) {
        List<Reader> rows = jdbc.query("SELECT * FROM readers WHERE card_no = ?", MAPPER, cardNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean phoneExists(String phone) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM readers WHERE phone = ? AND is_deleted = 0",
                Integer.class, phone);
        return n != null && n > 0;
    }

    public ListResult<Reader> list(String q, String type, int page, int pageSize, String today) {
        StringBuilder where = new StringBuilder(" r.is_deleted = 0");
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND (r.name LIKE ? OR r.card_no LIKE ? OR r.phone LIKE ? OR r.department LIKE ?)");
            for (int i = 0; i < 4; i++) {
                params.add("%" + q.trim() + "%");
            }
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND r.reader_type = ?");
            params.add(type.trim());
        }
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM readers r WHERE" + where, Integer.class, params.toArray());
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        int current = Math.min(page, pages);
        List<Object> pageParams = new ArrayList<>();
        pageParams.add(today);   // overdue_count 子查询要用
        pageParams.addAll(params);
        pageParams.add(pageSize);
        pageParams.add((current - 1) * pageSize);
        List<Reader> readers = jdbc.query(
                "SELECT r.*, (SELECT COUNT(*) FROM borrow_records br WHERE br.reader_id = r.id "
                        + "AND br.return_date IS NULL) AS borrowing, "
                        + "(SELECT COUNT(*) FROM borrow_records br WHERE br.reader_id = r.id "
                        + "AND br.return_date IS NULL AND br.due_date < ?) AS overdue_count "
                        + "FROM readers r WHERE" + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?",
                MAPPER, pageParams.toArray());
        return new ListResult<>(total, pages, current, readers);
    }

    public List<String> types() {
        return jdbc.queryForList(
                "SELECT DISTINCT reader_type FROM readers WHERE reader_type IS NOT NULL "
                        + "AND reader_type <> '' AND is_deleted = 0 ORDER BY reader_type", String.class);
    }

    public void insert(Reader r) {
        jdbc.update("INSERT INTO readers(card_no, name, gender, reader_type, department, phone, "
                        + "email, status, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                r.getCardNo(), r.getName(), r.getGender(), r.getReaderType(), r.getDepartment(),
                r.getPhone(), r.getEmail(), r.getStatus(), DateUtil.nowStr());
    }

    /** 插入含密码与来源的读者（自助办卡）。返回自增 id。 */
    public int insertWithPassword(Reader r) {
        jdbc.update("INSERT INTO readers(card_no, name, gender, reader_type, department, phone, "
                        + "email, password_hash, status, source, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                r.getCardNo(), r.getName(), r.getGender(), r.getReaderType(), r.getDepartment(),
                r.getPhone(), r.getEmail(), r.getPasswordHash(), r.getStatus(), r.getSource(),
                DateUtil.nowStr());
        Integer id = jdbc.queryForObject(
                "SELECT id FROM readers WHERE card_no = ? ORDER BY id DESC LIMIT 1",
                Integer.class, r.getCardNo());
        return id == null ? 0 : id;
    }

    public void update(Reader r) {
        jdbc.update("UPDATE readers SET card_no=?, name=?, gender=?, reader_type=?, department=?, "
                        + "phone=?, email=?, status=?, is_deleted=0 WHERE id=?",
                r.getCardNo(), r.getName(), r.getGender(), r.getReaderType(), r.getDepartment(),
                r.getPhone(), r.getEmail(), r.getStatus(), r.getId());
    }

    public void updatePassword(int readerId, String passwordHash) {
        jdbc.update("UPDATE readers SET password_hash = ? WHERE id = ?", passwordHash, readerId);
    }

    public void updateEmail(int readerId, String email) {
        jdbc.update("UPDATE readers SET email = ? WHERE id = ?", email, readerId);
    }

    public void updatePhone(int readerId, String phone) {
        jdbc.update("UPDATE readers SET phone = ? WHERE id = ?", phone, readerId);
    }

    public void markDeleted(int readerId) {
        jdbc.update("UPDATE readers SET is_deleted = 1, status = '停用' WHERE id = ?", readerId);
    }

    public void delete(int readerId) {
        jdbc.update("DELETE FROM readers WHERE id = ?", readerId);
    }

    public void setStatus(int readerId, String status) {
        jdbc.update("UPDATE readers SET status = ? WHERE id = ?", status, readerId);
    }

    /** 读者历史借阅总条数。 */
    public int borrowHistoryCount(int readerId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records WHERE reader_id = ?", Integer.class, readerId);
        return n == null ? 0 : n;
    }

    /** 当前在借册数。 */
    public int borrowingCount(int readerId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records WHERE reader_id = ? AND return_date IS NULL",
                Integer.class, readerId);
        return n == null ? 0 : n;
    }

    /** 当前逾期册数。 */
    public int overdueCount(int readerId, String today) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records WHERE reader_id = ? AND return_date IS NULL "
                        + "AND due_date < ?", Integer.class, readerId, today);
        return n == null ? 0 : n;
    }

    /** 自动生成借书卡号：R + 年份后两位 + 4 位流水号。 */
    public String nextCardNo() {
        String prefix = "R" + java.time.Year.now().getValue() % 100;
        String row = jdbc.query(
                "SELECT card_no FROM readers WHERE card_no LIKE ? ORDER BY card_no DESC LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, prefix + "%");
        int seq = 1;
        if (row != null && row.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(row.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        for (int i = 0; i < 50; i++) {
            String cardNo = prefix + String.format("%04d", seq);
            if (findByCardNo(cardNo) == null) {
                return cardNo;
            }
            seq++;
        }
        return prefix + String.format("%04X", new java.security.SecureRandom().nextInt(0xFFFF));
    }
}
