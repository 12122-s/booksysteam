package com.library.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.library.util.TtlCache;

/**
 * 数据分析看板（管理端）：借阅趋势、分类热度、读者活跃度、按时归还率、
 * 借阅时长分布、图书周转率。折线/环形图的坐标在这里算好，模板只负责画。
 * 数据容忍 2 分钟延迟，走 120 秒 TTL 缓存。
 */
@Service
public class AnalyticsService {

    public static final int TREND_MONTHS = 12;          // 趋势图看近 12 个月
    public static final int CHART_WIDTH = 760;          // 折线图 SVG viewBox 宽
    public static final int CHART_HEIGHT = 240;         // 高
    public static final int CHART_PAD = 30;             // 上下留白
    public static final List<String> DONUT_COLORS =
            List.of("#16a34a", "#f59e0b", "#3b82f6", "#dc2626");

    /** 折线图数据。 */
    public record Trend(List<String> labels, List<String> labelsShort,
                        List<Integer> borrowed, List<Integer> returned,
                        String borrowedPoints, String returnedPoints,
                        int peak, int totalBorrowed) {
    }

    /** 条形榜条目（分类热度 / 读者活跃度 / 周转率共用）。 */
    public record BarRow(String name, String sub, double value, String display, double percent) {
    }

    /** 归还状态环形图。 */
    public record ReturnStatus(List<Map<String, Object>> segments, int total,
                               String gradient, double onTimeRate, double overdueRate, int finished) {
    }

    /** 时长分布。 */
    public record Durations(List<BarRow> buckets, int peak, double average) {
    }

    public record AnalyticsData(Trend trend, List<BarRow> categories, List<BarRow> readers,
                                List<BarRow> turnover, ReturnStatus returns, Durations durations) {
    }

    private final JdbcTemplate jdbc;
    private final TtlCache<AnalyticsData> cache = new TtlCache<>(120);

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalyticsData get() {
        return cache.getOrCompute(this::compute);
    }

    public void evict() {
        cache.evict();
    }

    /** 最近 count 个完整月份（不含当月）：当月没过完，画进去像“借阅量崩了”。 */
    private List<String[]> recentMonths(int count) {
        LocalDate last = LocalDate.now().minusMonths(1);
        List<String[]> months = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            LocalDate m = last.minusMonths(i);
            months.add(new String[]{
                    m.getYear() + "年" + m.getMonthValue() + "月",
                    String.format("%04d-%02d", m.getYear(), m.getMonthValue())});
        }
        return months;
    }

    private Trend trend() {
        List<String[]> months = recentMonths(TREND_MONTHS);
        String start = months.get(0)[1] + "-01";

        Map<String, Integer> borrowedMap = new HashMap<>();
        org.springframework.jdbc.core.RowCallbackHandler borrowedHandler = rs ->
                borrowedMap.put(rs.getString(1), rs.getInt(2));
        jdbc.query("SELECT LEFT(borrow_date,7) AS m, COUNT(*) AS c FROM borrow_records "
                + "WHERE borrow_date >= ? GROUP BY m", borrowedHandler, start);
        Map<String, Integer> returnedMap = new HashMap<>();
        org.springframework.jdbc.core.RowCallbackHandler returnedHandler = rs ->
                returnedMap.put(rs.getString(1), rs.getInt(2));
        jdbc.query("SELECT LEFT(return_date,7) AS m, COUNT(*) AS c FROM borrow_records "
                + "WHERE return_date IS NOT NULL AND return_date >= ? GROUP BY m", returnedHandler, start);

        List<String> labels = new ArrayList<>();
        List<String> labelsShort = new ArrayList<>();
        List<Integer> borrowed = new ArrayList<>();
        List<Integer> returned = new ArrayList<>();
        for (String[] month : months) {
            labels.add(month[0]);
            labelsShort.add(month[0].replace("年", "/").replace("月", ""));
            borrowed.add(borrowedMap.getOrDefault(month[1], 0));
            returned.add(returnedMap.getOrDefault(month[1], 0));
        }

        int peak = Math.max(borrowed.stream().max(Integer::compare).orElse(1),
                returned.stream().max(Integer::compare).orElse(1));
        peak = Math.max(peak, 1);
        double step = (CHART_WIDTH - CHART_PAD * 2.0) / Math.max(months.size() - 1, 1);

        return new Trend(labels, labelsShort, borrowed, returned,
                toPoints(borrowed, peak, step), toPoints(returned, peak, step),
                peak, borrowed.stream().mapToInt(Integer::intValue).sum());
    }

    private String toPoints(List<Integer> values, int peak, double step) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            double x = CHART_PAD + i * step;
            double y = CHART_HEIGHT - CHART_PAD - (values.get(i) / (double) peak)
                    * (CHART_HEIGHT - CHART_PAD * 2);
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%.1f,%.1f", x, y));
        }
        return sb.toString();
    }

    private List<BarRow> categoryRank() {
        List<BarRow> rows = new ArrayList<>();
        org.springframework.jdbc.core.RowCallbackHandler handler = rs ->
                rows.add(new BarRow(rs.getString(1), null, rs.getInt(2),
                        String.valueOf(rs.getInt(2)), 0));
        jdbc.query("""
                SELECT COALESCE(NULLIF(b.category,''),'未分类') AS name, COUNT(*) AS times
                FROM borrow_records br JOIN books b ON b.id = br.book_id
                GROUP BY name ORDER BY times DESC, name LIMIT 8
                """, handler);
        return withPercents(rows);
    }

    private List<BarRow> readerRank() {
        List<BarRow> rows = new ArrayList<>();
        org.springframework.jdbc.core.RowCallbackHandler handler = rs ->
                rows.add(new BarRow(rs.getString(1),
                        rs.getString(3) == null || rs.getString(3).isBlank() ? rs.getString(2) : rs.getString(3),
                        rs.getInt(4), String.valueOf(rs.getInt(4)), 0));
        jdbc.query("""
                SELECT r.name, r.card_no, r.department, COUNT(*) AS times
                FROM borrow_records br JOIN readers r ON r.id = br.reader_id
                GROUP BY br.reader_id ORDER BY times DESC, r.name LIMIT 10
                """, handler);
        return withPercents(rows);
    }

    private List<BarRow> turnoverRank() {
        List<BarRow> rows = new ArrayList<>();
        org.springframework.jdbc.core.RowCallbackHandler handler = rs ->
                rows.add(new BarRow(rs.getString(1),
                        rs.getInt(2) + " 次 / " + rs.getInt(3) + " 册",
                        rs.getDouble(4), String.format("%.2f", rs.getDouble(4)), 0));
        jdbc.query("""
                SELECT b.title, COUNT(*) AS times, GREATEST(b.total_copies,1) AS copies,
                       ROUND(COUNT(*) / GREATEST(b.total_copies,1), 2) AS turnover
                FROM borrow_records br JOIN books b ON b.id = br.book_id
                GROUP BY br.book_id ORDER BY turnover DESC, times DESC LIMIT 8
                """, handler);
        return withPercents(rows);
    }

    private ReturnStatus returnStatus() {
        String today = LocalDate.now().toString();
        int onTime = scalar("""
                SELECT COUNT(*) FROM borrow_records
                WHERE return_date IS NOT NULL AND return_date <> '' AND return_date <= due_date""");
        int late = scalar("""
                SELECT COUNT(*) FROM borrow_records
                WHERE return_date IS NOT NULL AND return_date <> '' AND return_date > due_date""");
        int borrowing = scalar(
                "SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL AND due_date >= ?", today);
        int overdue = scalar(
                "SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL AND due_date < ?", today);

        String[] names = {"按时归还", "逾期归还", "在借未逾期", "在借已逾期"};
        int[] values = {onTime, late, borrowing, overdue};
        int total = onTime + late + borrowing + overdue;

        List<Map<String, Object>> segments = new ArrayList<>();
        StringBuilder gradient = new StringBuilder();
        double start = 0;
        for (int i = 0; i < 4; i++) {
            double end = total > 0 ? start + (values[i] / (double) total * 100) : start;
            if (total > 0) {
                if (gradient.length() > 0) {
                    gradient.append(", ");
                }
                gradient.append(DONUT_COLORS.get(i)).append(' ')
                        .append(String.format("%.2f%%", start)).append(' ')
                        .append(String.format("%.2f%%", end));
            }
            Map<String, Object> seg = new HashMap<>();
            seg.put("name", names[i]);
            seg.put("value", values[i]);
            seg.put("color", DONUT_COLORS.get(i));
            seg.put("percent", total > 0 ? Math.round(values[i] * 1000.0 / total) / 10.0 : 0.0);
            segments.add(seg);
            start = end;
        }

        int finished = onTime + late;
        return new ReturnStatus(segments, total,
                total > 0 ? "conic-gradient(" + gradient + ")" : "none",
                finished > 0 ? Math.round(onTime * 1000.0 / finished) / 10.0 : 0.0,
                finished > 0 ? Math.round(late * 1000.0 / finished) / 10.0 : 0.0,
                finished);
    }

    private Durations durations() {
        Map<String, Integer> counts = new HashMap<>();
        org.springframework.jdbc.core.RowCallbackHandler handler = rs ->
                counts.put(rs.getString(1), rs.getInt(2));
        jdbc.query("""
                SELECT bucket, COUNT(*) AS c FROM (
                  SELECT CASE
                    WHEN DATEDIFF(return_date, borrow_date) <= 7  THEN '一周内'
                    WHEN DATEDIFF(return_date, borrow_date) <= 14 THEN '8-14 天'
                    WHEN DATEDIFF(return_date, borrow_date) <= 30 THEN '15-30 天'
                    WHEN DATEDIFF(return_date, borrow_date) <= 60 THEN '31-60 天'
                    ELSE '60 天以上' END AS bucket
                  FROM borrow_records
                  WHERE return_date IS NOT NULL AND return_date <> '' AND borrow_date <> ''
                ) t GROUP BY bucket
                """, handler);
        List<BarRow> buckets = new ArrayList<>();
        for (String name : List.of("一周内", "8-14 天", "15-30 天", "31-60 天", "60 天以上")) {
            int v = counts.getOrDefault(name, 0);
            buckets.add(new BarRow(name, null, v, String.valueOf(v), 0));
        }
        buckets = withPercents(buckets);
        Double avg = jdbc.queryForObject("""
                SELECT AVG(DATEDIFF(return_date, borrow_date)) FROM borrow_records
                WHERE return_date IS NOT NULL AND return_date <> '' AND borrow_date <> ''
                """, Double.class);
        int peak = buckets.stream().mapToInt(b -> (int) b.value()).max().orElse(1);
        peak = Math.max(peak, 1);
        return new Durations(buckets, peak, avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0);
    }

    private List<BarRow> withPercents(List<BarRow> rows) {
        int max = (int) Math.ceil(rows.stream().mapToDouble(BarRow::value).max().orElse(1));
        final int top = Math.max(max, 1);
        return rows.stream()
                .map(r -> new BarRow(r.name(), r.sub(), r.value(), r.display(),
                        Math.round(r.value() * 1000.0 / top) / 10.0))
                .toList();
    }

    private int scalar(String sql, Object... params) {
        Integer n = jdbc.queryForObject(sql, Integer.class, params);
        return n == null ? 0 : n;
    }

    private AnalyticsData compute() {
        return new AnalyticsData(trend(), categoryRank(), readerRank(),
                turnoverRank(), returnStatus(), durations());
    }
}
