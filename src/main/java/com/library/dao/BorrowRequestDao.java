package com.library.dao;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.library.entity.BorrowRequest;
import com.library.util.DateUtil;

/** 借阅申请（排队）表数据访问。 */
@Repository
public class BorrowRequestDao {

    private static final BeanPropertyRowMapper<BorrowRequest> MAPPER =
            new BeanPropertyRowMapper<>(BorrowRequest.class);

    private final JdbcTemplate jdbc;

    public BorrowRequestDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BorrowRequest findById(int requestId) {
        List<BorrowRequest> rows = jdbc.query(
                "SELECT rq.*, b.title AS book_title, r.name AS reader_name, r.email, r.phone "
                        + "FROM borrow_requests rq JOIN books b ON b.id = rq.book_id "
                        + "JOIN readers r ON r.id = rq.reader_id WHERE rq.id = ?", MAPPER, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean existsPending(int readerId, int bookId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_requests WHERE reader_id = ? AND book_id = ? "
                        + "AND status IN ('待办理', '可借')", Integer.class, readerId, bookId);
        return n != null && n > 0;
    }

    public void insert(int bookId, int readerId, String note) {
        jdbc.update("INSERT INTO borrow_requests(book_id, reader_id, status, note, created_at) "
                + "VALUES (?,?, '待办理', ?, ?)", bookId, readerId, note, DateUtil.nowStr());
    }

    public void cancel(int requestId) {
        jdbc.update("UPDATE borrow_requests SET status = '已取消', done_at = ? WHERE id = ?",
                DateUtil.nowStr(), requestId);
    }

    /** 管理端申请列表（待办理排前面）。 */
    public List<BorrowRequest> list(String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT rq.*, b.title AS book_title, b.author AS book_author, b.available_copies, "
                        + "b.location, r.name AS reader_name, r.card_no, r.phone "
                        + "FROM borrow_requests rq JOIN books b ON b.id = rq.book_id "
                        + "JOIN readers r ON r.id = rq.reader_id");
        if (status != null && !status.isBlank()) {
            sql.append(" WHERE rq.status = ?");
            return jdbc.query(sql + " ORDER BY (rq.status = '待办理') DESC, rq.id ASC",
                    MAPPER, status.trim());
        }
        return jdbc.query(sql + " ORDER BY (rq.status = '待办理') DESC, rq.id ASC", MAPPER);
    }

    /** 各状态计数：全部 / 待办+可借 / 可借 / 已办理 / 已取消。 */
    public int[] counts() {
        int all = count("SELECT COUNT(*) FROM borrow_requests");
        int pending = count("SELECT COUNT(*) FROM borrow_requests WHERE status IN ('待办理', '可借')");
        int ready = count("SELECT COUNT(*) FROM borrow_requests WHERE status = '可借'");
        int done = count("SELECT COUNT(*) FROM borrow_requests WHERE status = '已办理'");
        int cancelled = count("SELECT COUNT(*) FROM borrow_requests WHERE status = '已取消'");
        return new int[]{all, pending, ready, done, cancelled};
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    /** 导航徽标：待办理（含可借）的申请数。 */
    public int countPending() {
        return count("SELECT COUNT(*) FROM borrow_requests WHERE status = '待办理'");
    }

    /** 办完借出后，把这个读者这本书的待办申请标成已办理（“待办理”和“可借”都认）。 */
    public void completeFor(int readerId, int bookId) {
        jdbc.update("UPDATE borrow_requests SET status = '已办理', done_at = ? "
                        + "WHERE reader_id = ? AND book_id = ? AND status IN ('待办理', '可借')",
                DateUtil.nowStr(), readerId, bookId);
    }

    /** 超期未办理的申请自动取消，返回取消条数。 */
    public int autoCancelExpired(int expireDays) {
        if (expireDays <= 0) {
            return 0;
        }
        String deadline = java.time.LocalDateTime.now()
                .minusDays(expireDays).format(DateUtil.DATETIME);
        return jdbc.update("UPDATE borrow_requests SET status = '已取消', done_at = ?, "
                        + "note = CONCAT(COALESCE(note,''), '（超过 ', ?, ' 天未办理，自动取消）') "
                        + "WHERE status = '待办理' AND created_at < ?",
                DateUtil.nowStr(), expireDays, deadline);
    }

    /** 还书时：这本书最早的“待办理”申请标成“可借”（书已到馆），返回那条申请。 */
    public BorrowRequest markFirstWaitingReady(int bookId) {
        BorrowRequest first = jdbc.query(
                "SELECT rq.id, r.name AS reader_name, r.email, r.phone, b.title AS book_title "
                        + "FROM borrow_requests rq JOIN readers r ON r.id = rq.reader_id "
                        + "JOIN books b ON b.id = rq.book_id WHERE rq.book_id = ? AND rq.status = '待办理' "
                        + "ORDER BY rq.id ASC LIMIT 1",
                rs -> rs.next() ? mapReady(rs) : null, bookId);
        if (first == null) {
            return null;
        }
        jdbc.update("UPDATE borrow_requests SET status = '可借', ready_at = ? WHERE id = ?",
                DateUtil.nowStr(), first.getId());
        return first;
    }

    private BorrowRequest mapReady(java.sql.ResultSet rs) throws java.sql.SQLException {
        BorrowRequest rq = new BorrowRequest();
        rq.setId(rs.getInt("id"));
        rq.setReaderName(rs.getString("reader_name"));
        rq.setEmail(rs.getString("email"));
        rq.setPhone(rs.getString("phone"));
        rq.setBookTitle(rs.getString("book_title"));
        return rq;
    }

    /** 这本书所有还在排队的人（还书时提示管理员用）。 */
    public record WaitingReader(String readerName, String phone) {
    }

    public List<WaitingReader> waitingList(int bookId) {
        return jdbc.query("SELECT r.name AS reader_name, r.phone FROM borrow_requests rq "
                        + "JOIN readers r ON r.id = rq.reader_id WHERE rq.book_id = ? "
                        + "AND rq.status IN ('待办理', '可借') ORDER BY rq.id ASC",
                (rs, i) -> new WaitingReader(rs.getString(1), rs.getString(2)), bookId);
    }

    /** 读者自己的申请（待办理/可借状态）。 */
    public List<BorrowRequest> pendingByReader(int readerId) {
        return jdbc.query("SELECT rq.*, b.title AS book_title, b.author AS book_author "
                        + "FROM borrow_requests rq JOIN books b ON b.id = rq.book_id "
                        + "WHERE rq.reader_id = ? AND rq.status IN ('待办理', '可借') ORDER BY rq.id DESC",
                MAPPER, readerId);
    }

    public BorrowRequest findByIdAndReader(int requestId, int readerId) {
        List<BorrowRequest> rows = jdbc.query(
                "SELECT * FROM borrow_requests WHERE id = ? AND reader_id = ?", MAPPER, requestId, readerId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
