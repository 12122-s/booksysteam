package com.library.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：从 JDBC URL 解析数据库名。
 *
 * <p>这里的用例锁住的是一个真实发生过的 bug —— URL 的查询参数里带
 * {@code serverTimezone=Asia/Shanghai}，用 lastIndexOf('/') 会把最后那个斜杠
 * 当成库名分隔符，解析出 "Shanghai&connectTimeout=8000"，于是自动建库建错、
 * 应用在全新环境上启动失败。
 */
class DatabaseInitializerTest {

    /** 与 application.yml 里默认 URL 完全一致（含 serverTimezone=Asia/Shanghai）。 */
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/library_system"
            + "?useUnicode=true&characterEncoding=utf8&useSSL=false"
            + "&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=8000";

    @Test
    @DisplayName("查询参数里有 Asia/Shanghai 时仍能取对库名")
    void parsesDbNameWhenQueryContainsSlash() {
        assertEquals("library_system", DatabaseInitializer.dbNameFromUrl(URL));
    }

    @Test
    @DisplayName("没有查询参数也能取对")
    void parsesDbNameWithoutQuery() {
        assertEquals("library_system",
                DatabaseInitializer.dbNameFromUrl("jdbc:mysql://127.0.0.1:3306/library_system"));
    }

    @Test
    @DisplayName("库名绝不能从查询参数里取（历史 bug：曾把 Shanghai 当成库名）")
    void neverTakesDbNameFromQueryString() {
        String name = DatabaseInitializer.dbNameFromUrl(URL);
        assertFalse(name.contains("Shanghai"), "库名里不该出现 Shanghai");
        assertFalse(name.contains("&"), "库名里不该带 & 参数");
        assertFalse(name.contains("?"), "库名里不该带查询串");
    }

    @Test
    @DisplayName("取不到库名时返回空串，而不是抛异常")
    void returnsBlankForMalformedUrl() {
        assertEquals("", DatabaseInitializer.dbNameFromUrl("jdbc:mysql://127.0.0.1:3306"));
    }

    @Test
    @DisplayName("去掉库名后得到服务器地址，供建库连接使用")
    void buildsServerUrlWithoutDatabase() {
        assertEquals(
                "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8"
                + "&useSSL=false&allowPublicKeyRetrieval=true",
                DatabaseInitializer.serverUrlOf(URL));
    }
}
