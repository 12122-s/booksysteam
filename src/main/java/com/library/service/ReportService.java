package com.library.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import com.library.util.TtlCache;

/**
 * 统计报表（管理端）：分类分布、借阅排行、6 个月趋势、库存与罚金汇总。
 * 全部是多表 JOIN + 分组统计，结果用 60 秒 TTL 缓存 —— 报表允许片刻旧数据，
 * 用它换掉每次刷新都全表扫描的开销（与 Python 版的取向一致）。
 */
@Service
public class ReportService {

    /** 一行分类统计。 */
    public record CategoryRow(String category, int kinds, int copies, int available, double percent) {
    }

    /** 一行排行榜（热门书 / 活跃读者）。 */
    public record RankRow(String name, String sub, int times, double percent) {
    }

    /** 一个月的借阅量。 */
    public record MonthRow(String label, int value, double percent) {
    }

    public record ReportData(
            List<CategoryRow> catRows,
            List<RankRow> hotBooks,
            List<RankRow> hotReaders,
            List<MonthRow> monthly,
            Map<String, Long> totals) {
    }

    private final JdbcTemplate jdbc;
    private final TtlCache<ReportData> cache = new TtlCache<>(60);

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReportData get() {
        return cache.getOrCompute(this::compute);
    }

    public void evict() {
        cache.evict();
    }

    private ReportData compute() {
        // 分类分布（含条形图宽度百分比）
        List<CategoryRow> catRaw = new ArrayList<>();
        RowCallbackHandler catHandler = rs -> catRaw.add(new CategoryRow(rs.getString("category"),
                rs.getInt("kinds"), rs.getInt("copies"), rs.getInt("available"), 0));
        jdbc.query("""
                SELECT COALESCE(NULLIF(category,''),'未分类') AS category,
                       COUNT(*) AS kinds, COALESCE(SUM(total_copies),0) AS copies,
                       COALESCE(SUM(available_copies),0) AS available
                FROM books WHERE is_deleted = 0
                GROUP BY COALESCE(NULLIF(category,''),'未分类')
                ORDER BY copies DESC
                """, catHandler);
        List<CategoryRow> catRows = new ArrayList<>();
        int maxCopies = Math.max(catRaw.stream().mapToInt(CategoryRow::copies).max().orElse(1), 1);
        for (CategoryRow r : catRaw) {
            catRows.add(new CategoryRow(r.category(), r.kinds(), r.copies(), r.available(),
                    percent(r.copies(), maxCopies)));
        }

        // 热门书
        List<RankRow> bookRaw = new ArrayList<>();
        RowCallbackHandler bookHandler = rs ->
                bookRaw.add(new RankRow(rs.getString(1), rs.getString(2), rs.getInt(3), 0));
        jdbc.query("""
                SELECT b.title, b.author, COUNT(*) AS times
                FROM borrow_records br JOIN books b ON b.id = br.book_id
                GROUP BY br.book_id ORDER BY times DESC, b.title LIMIT 10
                """, bookHandler);
        List<RankRow> hotBooks = withPercents(bookRaw);

        // 活跃读者
        List<RankRow> readerRaw = new ArrayList<>();
        RowCallbackHandler readerHandler = rs -> readerRaw.add(new RankRow(rs.getString(1),
                rs.getString(3) == null || rs.getString(3).isBlank() ? rs.getString(2) : rs.getString(3),
                rs.getInt(4), 0));
        jdbc.query("""
                SELECT r.name, r.card_no, r.department, COUNT(*) AS times
                FROM borrow_records br JOIN readers r ON r.id = br.reader_id
                GROUP BY br.reader_id ORDER BY times DESC, r.name LIMIT 10
                """, readerHandler);
        List<RankRow> hotReaders = withPercents(readerRaw);

        // 近 6 个月借出量（当月也计入，与 Python 版报表口径一致）
        List<MonthRow> monthRaw = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = today.minusMonths(i);
            String prefix = String.format("%04d-%02d", month.getYear(), month.getMonthValue());
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM borrow_records WHERE borrow_date LIKE ?",
                    Integer.class, prefix + "%");
            monthRaw.add(new MonthRow(month.getYear() + "年" + month.getMonthValue() + "月",
                    count == null ? 0 : count, 0));
        }
        List<MonthRow> monthly = new ArrayList<>();
        int maxMonth = Math.max(monthRaw.stream().mapToInt(MonthRow::value).max().orElse(1), 1);
        for (MonthRow m : monthRaw) {
            monthly.add(new MonthRow(m.label(), m.value(), percent(m.value(), maxMonth)));
        }

        // 库存与罚金汇总
        String todayStr = today.toString();
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("copies", scalar("SELECT COALESCE(SUM(total_copies),0) FROM books WHERE is_deleted = 0"));
        totals.put("available", scalar("SELECT COALESCE(SUM(available_copies),0) FROM books WHERE is_deleted = 0"));
        totals.put("borrowed", scalar("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL"));
        totals.put("overdue", scalar("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL AND due_date < ?", todayStr));
        totals.put("records", scalar("SELECT COUNT(*) FROM borrow_records"));
        totals.put("fine_total", scalar("SELECT COALESCE(SUM(fine_amount),0) FROM borrow_records"));
        totals.put("fine_paid", scalar("SELECT COALESCE(SUM(fine_amount),0) FROM borrow_records WHERE COALESCE(fine_paid,0) = 1"));
        totals.put("fine_unpaid", scalar("""
                SELECT COALESCE(SUM(fine_amount),0) FROM borrow_records
                WHERE COALESCE(fine_paid,0) = 0
                  AND (fine_amount > 0 OR (return_date IS NULL AND due_date < ?))
                """, todayStr));
        totals.put("readers", scalar("SELECT COUNT(*) FROM readers"));

        return new ReportData(catRows, hotBooks, hotReaders, monthly, totals);
    }

    private static double percent(double value, double top) {
        return Math.round(value * 1000.0 / top) / 10.0;
    }

    private List<RankRow> withPercents(List<RankRow> rows) {
        int top = Math.max(rows.stream().mapToInt(RankRow::times).max().orElse(1), 1);
        List<RankRow> result = new ArrayList<>();
        for (RankRow r : rows) {
            result.add(new RankRow(r.name(), r.sub(), r.times(), percent(r.times(), top)));
        }
        return result;
    }

    private long scalar(String sql, Object... params) {
        Long v = jdbc.queryForObject(sql, Long.class, params);
        return v == null ? 0 : v;
    }
}
