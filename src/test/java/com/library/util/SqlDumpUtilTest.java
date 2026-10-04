package com.library.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SqlDumpUtilTest {

    @Test
    void quotesNull() {
        assertEquals("NULL", SqlDumpUtil.quoteSql(null));
    }

    @Test
    void numbersAreBare() {
        assertEquals("3", SqlDumpUtil.quoteSql(3));
        assertEquals("2.5", SqlDumpUtil.quoteSql(2.5));
        assertEquals("2.5", SqlDumpUtil.quoteSql(2.5));
    }

    @Test
    void escapesLikePythonQuoteSql() {
        // 与 Python 版 quote_sql 的转义规则逐字对齐（单引号转义为 \'），备份文件才能两种语言互通
        assertEquals("'It\\'s'", SqlDumpUtil.quoteSql("It's"));
        assertEquals("'a\\\\b'", SqlDumpUtil.quoteSql("a\\b"));
        assertEquals("'line1\\nline2'", SqlDumpUtil.quoteSql("line1\nline2"));
        assertEquals("'line1\\nline2'", SqlDumpUtil.quoteSql("line1\r\nline2"));
        assertEquals("'col1\\tcol2'", SqlDumpUtil.quoteSql("col1\tcol2"));
        assertEquals("'中文'", SqlDumpUtil.quoteSql("中文"));
    }
}
