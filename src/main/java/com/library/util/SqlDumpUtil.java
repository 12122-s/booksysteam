package com.library.util;

/**
 * 备份 SQL 的值转义规则：反斜杠双写、单引号转义为 \'、换行/回车/制表符转成 \n 与 \t，
 * 转义后的字面量可以被 MySQL 直接执行，用于导出与恢复备份。
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
