package com.library.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期工具。
 *
 * <p>约定：借阅日期、应还日期等在数据库里存 "YYYY-MM-DD" 字符串，
 * 时间戳存 "YYYY-MM-DD HH:mm:ss" 字符串，与表单、Excel 导入导出格式完全一致，
 * 不需要来回转换。解析时对脏数据做容错（返回 null 而不是抛异常）。
 */
public final class DateUtil {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateUtil() {
    }

    /** 解析 "YYYY-MM-DD"，格式不对返回 null（不抛异常）。 */
    public static LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString().trim(), DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 解析 "YYYY-MM-DD HH:mm:ss"，格式不对返回 null。 */
    public static LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        // "2026-01-02T03:04:05" 也能接受（空格分隔和 T 分隔两种写法都认）
        text = text.replace('T', ' ');
        try {
            return LocalDateTime.parse(text, DATETIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 今天的日期字符串。 */
    public static String todayStr() {
        return LocalDate.now().format(DATE);
    }

    /** 当前时间字符串（到秒）。 */
    public static String nowStr() {
        return LocalDateTime.now().format(DATETIME);
    }

    /** 指定日期转字符串。 */
    public static String format(LocalDate date) {
        return date == null ? null : date.format(DATE);
    }

    /** 两个日期字符串相差的天数（a - b），任一个非法返回 null。 */
    public static Integer daysBetween(String a, String b) {
        LocalDate da = parseDate(a);
        LocalDate db = parseDate(b);
        if (da == null || db == null) {
            return null;
        }
        return (int) (da.toEpochDay() - db.toEpochDay());
    }
}
