package com.library.dao;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.library.entity.Book;

/** 图书表数据访问。 */
@Repository
public class BookDao {

    private final JdbcTemplate jdbc;

    public BookDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final BeanPropertyRowMapper<Book> MAPPER = new BeanPropertyRowMapper<>(Book.class);

    /** 通用联查（推荐模块等场景直接传完整 SQL）。 */
    public List<Book> listAll(String sql, Object... params) {
        return jdbc.query(sql, MAPPER, params);
    }

    public Book findById(int id) {
        List<Book> rows = jdbc.query("SELECT * FROM books WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 图书列表：关键字（书名/作者/ISBN/出版社/分类）+ 分类 + 仅可借，分页。 */
    public ListResult<Book> list(String q, String category, boolean onlyAvailable, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" b.is_deleted = 0");
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND (b.title LIKE ? OR b.author LIKE ? OR b.isbn LIKE ? OR b.publisher LIKE ? OR b.category LIKE ?)");
            for (int i = 0; i < 5; i++) {
                params.add("%" + q.trim() + "%");
            }
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND b.category = ?");
            params.add(category.trim());
        }
        if (onlyAvailable) {
            where.append(" AND b.available_copies > 0");
        }
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM books b WHERE" + where, Integer.class, params.toArray());
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        int current = Math.min(page, pages);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add((current - 1) * pageSize);
        List<Book> books = jdbc.query(
                "SELECT b.*, (SELECT COUNT(*) FROM borrow_records br WHERE br.book_id = b.id "
                        + "AND br.return_date IS NULL) AS out_count FROM books b WHERE" + where
                        + " ORDER BY b.id DESC LIMIT ? OFFSET ?",
                MAPPER, pageParams.toArray());
        return new ListResult<>(total, pages, current, books);
    }

    /** 全部分类（去重，非空）。 */
    public List<String> categories() {
        return jdbc.queryForList(
                "SELECT DISTINCT category FROM books WHERE category IS NOT NULL AND category <> '' "
                        + "AND is_deleted = 0 ORDER BY category", String.class);
    }

    public void insert(Book b, String createdAt) {
        jdbc.update("INSERT INTO books(title, author, category, isbn, publisher, publish_year, "
                        + "price, location, summary, total_copies, available_copies, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                b.getTitle(), b.getAuthor(), b.getCategory(), b.getIsbn(), b.getPublisher(),
                b.getPublishYear(), b.getPrice(), b.getLocation(), b.getSummary(),
                b.getTotalCopies(), b.getAvailableCopies(), createdAt);
    }

    public void update(Book b) {
        jdbc.update("UPDATE books SET title=?, author=?, category=?, isbn=?, publisher=?, "
                        + "publish_year=?, price=?, location=?, summary=?, total_copies=?, available_copies=? "
                        + "WHERE id=?",
                b.getTitle(), b.getAuthor(), b.getCategory(), b.getIsbn(), b.getPublisher(),
                b.getPublishYear(), b.getPrice(), b.getLocation(), b.getSummary(),
                b.getTotalCopies(), b.getAvailableCopies(), b.getId());
    }

    /** 有借阅历史的书做“下架”；返回历史条数供调用方判断走哪条路。 */
    public int borrowHistoryCount(int bookId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records WHERE book_id = ?", Integer.class, bookId);
        return n == null ? 0 : n;
    }

    public void markDeleted(int bookId) {
        jdbc.update("UPDATE books SET is_deleted = 1 WHERE id = ?", bookId);
    }

    public void delete(int bookId) {
        jdbc.update("DELETE FROM books WHERE id = ?", bookId);
    }

    /** 按关键字搜书（书标打印用），最多 limit 本。 */
    public List<Book> searchForLabels(String keyword, int limit) {
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            return jdbc.query("SELECT * FROM books WHERE is_deleted = 0 AND (title LIKE ? OR author LIKE ? "
                    + "OR isbn LIKE ?) ORDER BY location, title LIMIT ?", MAPPER, like, like, like, limit);
        }
        return jdbc.query("SELECT * FROM books WHERE is_deleted = 0 ORDER BY location, title LIMIT ?",
                MAPPER, limit);
    }

    /** 当前在借（未还）的册数。 */
    public int currentBorrowedOut(int bookId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records WHERE book_id = ? AND return_date IS NULL",
                Integer.class, bookId);
        return n == null ? 0 : n;
    }

    /** 借出后可借册数 -1。 */
    public void decrementAvailable(int bookId) {
        jdbc.update("UPDATE books SET available_copies = available_copies - 1 WHERE id = ?", bookId);
    }

    /** 更新封面文件名（null = 删除封面）。 */
    public void updateCover(int bookId, String cover) {
        jdbc.update("UPDATE books SET cover = ? WHERE id = ?", cover, bookId);
    }

    /** 还书后可借册数 +1，但不超过总册数。 */
    public void incrementAvailable(int bookId) {
        jdbc.update("UPDATE books SET available_copies = LEAST(total_copies, available_copies + 1) "
                + "WHERE id = ?", bookId);
    }

    /** 原子扣减：只有在还有库存时才 -1，返回受影响行数（防两人抢最后一册）。 */
    public int decrementAvailableIfAvailable(int bookId) {
        return jdbc.update("UPDATE books SET available_copies = available_copies - 1 "
                + "WHERE id = ? AND available_copies > 0", bookId);
    }

    /** 分页查询的统一返回结构。 */
    public record ListResult<T>(int total, int pages, int page, List<T> items) {
    }
}
