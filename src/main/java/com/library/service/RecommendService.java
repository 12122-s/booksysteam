package com.library.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.library.dao.BookDao;
import com.library.dao.BorrowDao;
import com.library.entity.Book;

/**
 * 图书推荐：“猜你可能想看”。
 *
 * <p>用基于内容的推荐（不是协同过滤）：小图书室借阅记录少，协同过滤在数据稀疏时
 * 推荐质量差、新读者直接推不出来。三步走：
 * 1) 按借阅历史排出读者最偏好的前 3 个分类；
 * 2) 在这些分类里找“没借过、有库存”的书，按全馆热度排序；
 * 3) 不够时用全馆热门书补齐（冷启动兜底）。
 */
@Service
public class RecommendService {

    private static final int TOP_CATEGORIES = 3;

    /** 一条推荐：书 + 推荐理由。 */
    public record Recommendation(Book book, String reason, boolean personalised) {
    }

    private final BorrowDao borrowDao;
    private final BookDao bookDao;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public RecommendService(BorrowDao borrowDao, BookDao bookDao,
                            org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.borrowDao = borrowDao;
        this.bookDao = bookDao;
        this.jdbc = jdbc;
    }

    /** 读者历史上借过的所有书 id（推荐时排除）。 */
    private Set<Integer> borrowedBookIds(int readerId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT DISTINCT book_id FROM borrow_records WHERE reader_id = ?",
                Integer.class, readerId));
    }

    /** 读者最偏好的前 N 个分类。 */
    private List<String> favouriteCategories(int readerId) {
        return jdbc.queryForList(
                "SELECT b.category FROM borrow_records br JOIN books b ON b.id = br.book_id "
                        + "WHERE br.reader_id = ? AND b.category IS NOT NULL AND b.category <> '' "
                        + "GROUP BY b.category ORDER BY COUNT(*) DESC, b.category LIMIT " + TOP_CATEGORIES,
                String.class, readerId);
    }

    private List<Book> hotBooksInCategory(String category, int limit) {
        return bookDao.listAll(
                "SELECT b.*, COALESCE(h.times,0) AS hot FROM books b LEFT JOIN "
                        + "(SELECT book_id, COUNT(*) AS times FROM borrow_records GROUP BY book_id) h "
                        + "ON h.book_id = b.id WHERE b.is_deleted = 0 AND b.available_copies > 0 "
                        + "AND b.category = ? ORDER BY hot DESC, b.id DESC LIMIT " + limit, category);
    }

    private List<Book> hotBooks(int limit) {
        return bookDao.listAll(
                "SELECT b.*, COALESCE(h.times,0) AS hot FROM books b LEFT JOIN "
                        + "(SELECT book_id, COUNT(*) AS times FROM borrow_records GROUP BY book_id) h "
                        + "ON h.book_id = b.id WHERE b.is_deleted = 0 AND b.available_copies > 0 "
                        + "ORDER BY hot DESC, b.id DESC LIMIT " + limit);
    }

    /** 给一位读者生成推荐列表（页面每次现算，量级小，无需缓存）。 */
    public List<Recommendation> recommendFor(int readerId, int limit) {
        Set<Integer> borrowed = borrowedBookIds(readerId);
        List<String> favourites = favouriteCategories(readerId);

        List<Recommendation> picked = new ArrayList<>();
        Set<Integer> pickedIds = new HashSet<>();

        for (String category : favourites) {
            for (Book book : hotBooksInCategory(category, limit)) {
                if (borrowed.contains(book.getId()) || pickedIds.contains(book.getId())) {
                    continue;
                }
                picked.add(new Recommendation(book, "你常借「" + category + "」类的书", true));
                pickedIds.add(book.getId());
                if (picked.size() >= limit) {
                    return picked;
                }
            }
        }
        // 不够就补热门：没有任何借阅记录的新读者会走到这里（冷启动兜底）
        for (Book book : hotBooks(limit * 2)) {
            if (borrowed.contains(book.getId()) || pickedIds.contains(book.getId())) {
                continue;
            }
            picked.add(new Recommendation(book, "馆内热门，大家借得多", false));
            pickedIds.add(book.getId());
            if (picked.size() >= limit) {
                break;
            }
        }
        return picked;
    }
}
