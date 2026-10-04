package com.library.dao;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.library.dao.BookDao.ListResult;
import com.library.entity.BorrowRecord;

/** 借阅记录表数据访问。 */
@Repository
public class BorrowDao {

    private static final BeanPropertyRowMapper<BorrowRecord> MAPPER =
            new BeanPropertyRowMapper<>(BorrowRecord.class);

    private final JdbcTemplate jdbc;

    public BorrowDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BorrowRecord findById(int recordId) {
        List<BorrowRecord> rows = jdbc.query(
                "SELECT br.*, b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, "
                        + "b.location AS book_location, r.name AS reader_name, r.card_no, r.phone, "
                        + "r.department FROM borrow_records br JOIN books b ON b.id = br.book_id "
                        + "JOIN readers r ON r.id = br.reader_id WHERE br.id = ?",
                MAPPER, recordId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public BorrowRecord findByIdAndReader(int recordId, int readerId) {
        List<BorrowRecord> rows = jdbc.query(
                "SELECT br.*, b.title AS book_title FROM borrow_records br "
                        + "JOIN books b ON b.id = br.book_id WHERE br.id = ? AND br.reader_id = ?",
                MAPPER, recordId, readerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 借阅记录列表：状态筛选 + 关键字，分页。 */
    public ListResult<BorrowRecord> list(String status, String q, int page, int pageSize, String today) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if ("borrowing".equals(status)) {
            where.add("br.return_date IS NULL AND br.due_date >= ?");
            params.add(today);
        } else if ("overdue".equals(status)) {
            where.add("br.return_date IS NULL AND br.due_date < ?");
            params.add(today);
        } else if ("returned".equals(status)) {
            where.add("br.return_date IS NOT NULL");
        }
        if (q != null && !q.isBlank()) {
            where.add("(b.title LIKE ? OR r.name LIKE ? OR r.card_no LIKE ? OR b.isbn LIKE ?)");
            for (int i = 0; i < 4; i++) {
                params.add("%" + q.trim() + "%");
            }
        }
        String sqlWhere = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records br JOIN books b ON b.id = br.book_id "
                        + "JOIN readers r ON r.id = br.reader_id" + sqlWhere,
                Integer.class, params.toArray());
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        int current = Math.min(page, pages);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((current - 1) * pageSize);
        List<BorrowRecord> records = jdbc.query(
                "SELECT br.*, b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, "
                        + "r.name AS reader_name, r.card_no, r.phone FROM borrow_records br "
                        + "JOIN books b ON b.id = br.book_id JOIN readers r ON r.id = br.reader_id"
                        + sqlWhere
                        + " ORDER BY (br.return_date IS NOT NULL), br.due_date ASC, br.id DESC LIMIT ? OFFSET ?",
                MAPPER, pageParams.toArray());
        return new ListResult<>(total, pages, current, records);
    }

    /** 各状态数量（借阅页签）。 */
    public int[] counts(String today) {
        int all = count("SELECT COUNT(*) FROM borrow_records");
        int borrowing = count("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL AND due_date >= ?", today);
        int overdue = count("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL AND due_date < ?", today);
        int returned = count("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NOT NULL");
        return new int[]{all, borrowing, overdue, returned};
    }

    private int count(String sql, Object... params) {
        Integer n = jdbc.queryForObject(sql, Integer.class, params);
        return n == null ? 0 : n;
    }

    /** 导航徽标：当前逾期未还的条数。 */
    public int countOverdue(String today) {
        return count("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL AND due_date < ?", today);
    }

    /** 导航徽标：已归还但罚金未收的条数。 */
    public int countUnpaidFines() {
        return count("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NOT NULL "
                + "AND fine_amount > 0 AND COALESCE(fine_paid, 0) = 0");
    }

    /** 某书的活跃借阅（含读者信息），用于删除书/删除读者前的校验。 */
    public record ActiveBorrow(String readerName, String bookTitle, String dueDate) {
    }

    public List<ActiveBorrow> activeBorrowsForBook(int bookId) {
        return jdbc.query("SELECT r.name AS reader_name, br.due_date FROM borrow_records br "
                        + "JOIN readers r ON r.id = br.reader_id WHERE br.book_id = ? AND br.return_date IS NULL",
                (rs, i) -> new ActiveBorrow(rs.getString(1), null, rs.getString(2)), bookId);
    }

    public List<ActiveBorrow> activeBorrowsForReader(int readerId) {
        return jdbc.query("SELECT b.title AS book_title, br.due_date FROM borrow_records br "
                        + "JOIN books b ON b.id = br.book_id WHERE br.reader_id = ? AND br.return_date IS NULL",
                (rs, i) -> new ActiveBorrow(null, rs.getString(1), rs.getString(2)), readerId);
    }

    /** 读者是否正借着某本书（没还）。 */
    public boolean isCurrentlyBorrowing(int readerId, int bookId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records WHERE reader_id = ? AND book_id = ? "
                        + "AND return_date IS NULL", Integer.class, readerId, bookId);
        return n != null && n > 0;
    }

    public void insert(int bookId, int readerId, String borrowDate, String dueDate,
                       String source, String remark) {
        jdbc.update("INSERT INTO borrow_records(book_id, reader_id, borrow_date, due_date, source, remark) "
                + "VALUES (?,?,?,?,?,?)", bookId, readerId, borrowDate, dueDate, source, remark);
    }

    public void markReturned(int recordId, String returnDate, double fine) {
        jdbc.update("UPDATE borrow_records SET return_date = ?, fine_amount = ? WHERE id = ?",
                returnDate, fine, recordId);
    }

    public void renew(int recordId, String newDueDate) {
        jdbc.update("UPDATE borrow_records SET due_date = ?, renew_count = renew_count + 1 WHERE id = ?",
                newDueDate, recordId);
    }

    /** 某读者当前在借列表（读者端“我的借阅”）。 */
    public List<BorrowRecord> activeByReader(int readerId) {
        return jdbc.query(
                "SELECT br.*, b.title AS book_title FROM borrow_records br "
                        + "JOIN books b ON b.id = br.book_id WHERE br.reader_id = ? AND br.return_date IS NULL "
                        + "ORDER BY br.due_date ASC", MAPPER, readerId);
    }

    /** 某读者全部借阅历史。 */
    public List<BorrowRecord> allByReader(int readerId) {
        return jdbc.query(
                "SELECT br.*, b.title AS book_title, b.author AS book_author, b.location AS book_location "
                        + "FROM borrow_records br JOIN books b ON b.id = br.book_id "
                        + "WHERE br.reader_id = ? ORDER BY br.borrow_date DESC, br.id DESC", MAPPER, readerId);
    }

    /** 某本书的借阅历史。 */
    public List<BorrowRecord> allByBook(int bookId) {
        return jdbc.query(
                "SELECT br.*, r.name AS reader_name, r.card_no, b.title AS book_title "
                        + "FROM borrow_records br JOIN readers r ON r.id = br.reader_id "
                        + "JOIN books b ON b.id = br.book_id WHERE br.book_id = ? "
                        + "ORDER BY br.borrow_date DESC, br.id DESC", MAPPER, bookId);
    }

    /** 工作台/导出用：全部记录联查。 */
    public List<BorrowRecord> query(String sql, Object... params) {
        return jdbc.query(sql, MAPPER, params);
    }

    // ---- 罚金相关 ----

    public void markFinePaid(int recordId, String paidAt, String note) {
        jdbc.update("UPDATE borrow_records SET fine_paid = 1, fine_paid_at = ?, fine_note = ? WHERE id = ?",
                paidAt, note, recordId);
    }

    public void waiveFine(int recordId, String paidAt, String note) {
        jdbc.update("UPDATE borrow_records SET fine_paid = 1, fine_amount = 0, fine_paid_at = ?, "
                + "fine_note = ? WHERE id = ?", paidAt, note, recordId);
    }

    public void unmarkFinePaid(int recordId, String note) {
        jdbc.update("UPDATE borrow_records SET fine_paid = 0, fine_paid_at = NULL, fine_note = ? WHERE id = ?",
                note, recordId);
    }

    public void claimFine(int recordId, String now) {
        jdbc.update("UPDATE borrow_records SET fine_claim = '申请中', fine_claim_at = ? WHERE id = ?",
                now, recordId);
    }

    /** 罚金台账：归还且产生过罚金的记录。 */
    public List<BorrowRecord> fineLedger() {
        return jdbc.query(
                "SELECT br.*, b.title AS book_title, r.name AS reader_name, r.card_no, r.phone "
                        + "FROM borrow_records br JOIN books b ON b.id = br.book_id "
                        + "JOIN readers r ON r.id = br.reader_id "
                        + "WHERE br.return_date IS NOT NULL AND br.fine_amount > 0 "
                        + "ORDER BY br.return_date DESC, br.id DESC", MAPPER);
    }
}
