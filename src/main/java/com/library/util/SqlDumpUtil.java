package com.library.util;

/**
 * 备份 SQL 的值转义，与 Python 版 quote_sql() 逐字兼容，
 * 保证 Python 版导出的备份能在 Java 版恢复（反之亦然）。
 */
public final class SqlDumpUtil {

    private SqlDumpUtil() {
    }

    /** 把一个值转成 SQL 字面量：null → NULL，数字直接输出，字符串加引号并转义。 */
    public static String quoteSql(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number n) {
            return n.toString();
        }
        String text = value.toString()
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n")
                .replace("\t", "\\t");
        return "'" + text + "'";
    }
}
