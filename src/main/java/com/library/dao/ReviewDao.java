package com.library.dao;

import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.library.entity.BookReview;
import com.library.util.DateUtil;

/** 书评表数据访问。 */
@Repository
public class ReviewDao {

    private static final BeanPropertyRowMapper<BookReview> MAPPER =
            new BeanPropertyRowMapper<>(BookReview.class);

    private final JdbcTemplate jdbc;

    public ReviewDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 一本书的评分摘要（条数 + 平均分），hidden=0 才算。fullStars 供模板画星星。 */
    public record ReviewStats(int count, double avg) {
        public int getFullStars() {
            return (int) Math.round(avg);
        }
    }

    public ReviewStats statsOf(int bookId) {
        return jdbc.query("SELECT COUNT(*) n, COALESCE(AVG(rating),0) a FROM book_reviews "
                        + "WHERE book_id = ? AND hidden = 0",
                rs -> {
                    rs.next();
                    double avg = rs.getDouble("a");
                    return new ReviewStats(rs.getInt("n"),
                            Math.round(avg * 10.0) / 10.0);
                }, bookId);
    }

    /** 全馆所有书的评分摘要（读者书城列表用一次查完）。 */
    public java.util.Map<Integer, ReviewStats> statsAll() {
        java.util.Map<Integer, ReviewStats> map = new java.util.HashMap<>();
        jdbc.query("SELECT book_id, COUNT(*) n, AVG(rating) a FROM book_reviews WHERE hidden = 0 "
                + "GROUP BY book_id", rs -> {
            double avg = rs.getDouble("a");
            map.put(rs.getInt("book_id"),
                    new ReviewStats(rs.getInt("n"), Math.round(avg * 10.0) / 10.0));
        });
        return map;
    }

    /** 某本书最近的几条可见书评。 */
    public List<BookReview> latestOf(int bookId, int limit) {
        return jdbc.query("SELECT rv.*, r.name AS reader_name FROM book_reviews rv "
                + "JOIN readers r ON r.id = rv.reader_id WHERE rv.book_id = ? AND rv.hidden = 0 "
                + "ORDER BY rv.id DESC LIMIT " + limit, MAPPER, bookId);
    }

    /** 每本书最多 limitPerBook 条最近书评（读者书城“看看大家怎么说”用）。 */
    public java.util.Map<Integer, List<BookReview>> latestMap(int limitPerBook) {
        java.util.Map<Integer, List<BookReview>> map = new java.util.LinkedHashMap<>();
        jdbc.query("SELECT rv.book_id, rv.rating, rv.content, rv.created_at, r.name AS reader_name "
                + "FROM book_reviews rv JOIN readers r ON r.id = rv.reader_id WHERE rv.hidden = 0 "
                + "ORDER BY rv.id DESC", rs -> {
            int bookId = rs.getInt("book_id");
            List<BookReview> list = map.get(bookId);
            if (list == null) {
                list = new java.util.ArrayList<>();
                map.put(bookId, list);
            }
            if (list.size() < limitPerBook) {
                BookReview rv = new BookReview();
                rv.setBookId(bookId);
                rv.setRating(rs.getInt("rating"));
                rv.setContent(rs.getString("content"));
                rv.setCreatedAt(rs.getString("created_at"));
                rv.setReaderName(rs.getString("reader_name"));
                list.add(rv);
            }
        });
        return map;
    }

    public BookReview findMine(int bookId, int readerId) {
        List<BookReview> rows = jdbc.query(
                "SELECT * FROM book_reviews WHERE book_id = ? AND reader_id = ?",
                MAPPER, bookId, readerId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean hasBorrowed(int readerId, int bookId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM borrow_records WHERE reader_id = ? AND book_id = ? LIMIT 1",
                Integer.class, readerId, bookId);
        return n != null && n > 0;
    }

    public void upsert(int bookId, int readerId, int rating, String content, boolean update, Integer existingId) {
        String now = DateUtil.nowStr();
        if (update) {
            jdbc.update("UPDATE book_reviews SET rating = ?, content = ?, updated_at = ? WHERE id = ?",
                    rating, content, now, existingId);
        } else {
            jdbc.update("INSERT INTO book_reviews(book_id, reader_id, rating, content, created_at, updated_at) "
                    + "VALUES (?,?,?,?,?,?)", bookId, readerId, rating, content, now, now);
        }
    }

    /** 管理端书评列表（可搜索）。 */
    public List<BookReview> list(String q) {
        StringBuilder sql = new StringBuilder(
                "SELECT rv.*, b.title AS book_title, r.name AS reader_name, r.card_no "
                        + "FROM book_reviews rv JOIN books b ON b.id = rv.book_id "
                        + "JOIN readers r ON r.id = rv.reader_id");
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim() + "%";
            return jdbc.query(sql + " WHERE (b.title LIKE ? OR r.name LIKE ? OR rv.content LIKE ?) "
                    + "ORDER BY rv.id DESC LIMIT 300", MAPPER, like, like, like);
        }
        return jdbc.query(sql + " ORDER BY rv.id DESC LIMIT 300", MAPPER);
    }

    public BookReview findById(int reviewId) {
        List<BookReview> rows = jdbc.query("SELECT rv.*, b.title AS book_title FROM book_reviews rv "
                + "JOIN books b ON b.id = rv.book_id WHERE rv.id = ?", MAPPER, reviewId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void setHidden(int reviewId, int hidden) {
        jdbc.update("UPDATE book_reviews SET hidden = ? WHERE id = ?", hidden, reviewId);
    }

    public void delete(int reviewId) {
        jdbc.update("DELETE FROM book_reviews WHERE id = ?", reviewId);
    }
}
