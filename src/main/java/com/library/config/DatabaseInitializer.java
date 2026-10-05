package com.library.config;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import com.library.service.SettingsService;
import com.library.util.PasswordUtil;

/**
 * 数据库初始化：启动时自动建库、建表、补字段、补索引、写默认设置。
 * 已存在的一律跳过，不会动用户数据 —— 与 Python 版 init_db() 行为一致。
 * 这样部署时不需要手动执行任何 SQL 脚本。
 */
@Component
@Order(1)
public class DatabaseInitializer implements ApplicationRunner {

    private static final List<String> SCHEMA = List.of("""
            CREATE TABLE IF NOT EXISTS books (
                id               INT AUTO_INCREMENT PRIMARY KEY,
                title            VARCHAR(200) NOT NULL,
                author           VARCHAR(120),
                category         VARCHAR(60),
                isbn             VARCHAR(40),
                publisher        VARCHAR(120),
                publish_year     INT,
                price            DOUBLE,
                location         VARCHAR(60),
                total_copies     INT NOT NULL DEFAULT 1,
                available_copies INT NOT NULL DEFAULT 1,
                is_deleted       TINYINT NOT NULL DEFAULT 0,
                summary          TEXT,
                cover            VARCHAR(200),
                created_at       VARCHAR(30),
                INDEX idx_books_title (title),
                INDEX idx_books_deleted (is_deleted)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS readers (
                id            INT AUTO_INCREMENT PRIMARY KEY,
                card_no       VARCHAR(40) NOT NULL UNIQUE,
                name          VARCHAR(60) NOT NULL,
                gender        VARCHAR(6),
                reader_type   VARCHAR(30),
                department    VARCHAR(120),
                phone         VARCHAR(30),
                email         VARCHAR(120),
                password_hash VARCHAR(200),
                status        VARCHAR(20) NOT NULL DEFAULT '正常',
                is_deleted    TINYINT NOT NULL DEFAULT 0,
                source        VARCHAR(20) NOT NULL DEFAULT '管理员登记',
                created_at    VARCHAR(30),
                INDEX idx_readers_name (name),
                INDEX idx_readers_deleted (is_deleted)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS borrow_records (
                id          INT AUTO_INCREMENT PRIMARY KEY,
                book_id     INT NOT NULL,
                reader_id   INT NOT NULL,
                borrow_date VARCHAR(10) NOT NULL,
                due_date    VARCHAR(10) NOT NULL,
                return_date VARCHAR(10) NULL,
                renew_count INT NOT NULL DEFAULT 0,
                fine_amount DOUBLE NOT NULL DEFAULT 0,
                source      VARCHAR(20) NOT NULL DEFAULT '管理员',
                remark      VARCHAR(255),
                INDEX idx_br_book (book_id),
                INDEX idx_br_reader (reader_id),
                INDEX idx_br_return (return_date),
                INDEX idx_br_due (due_date),
                CONSTRAINT fk_br_book   FOREIGN KEY (book_id)   REFERENCES books(id),
                CONSTRAINT fk_br_reader FOREIGN KEY (reader_id) REFERENCES readers(id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS settings (
                `key`   VARCHAR(60) PRIMARY KEY,
                `value` VARCHAR(255)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS password_resets (
                id         INT AUTO_INCREMENT PRIMARY KEY,
                reader_id  INT NOT NULL,
                code       VARCHAR(10) NOT NULL,
                expire_at  VARCHAR(20) NOT NULL,
                used       TINYINT NOT NULL DEFAULT 0,
                attempts   INT NOT NULL DEFAULT 0,
                created_at VARCHAR(30),
                INDEX idx_pr_reader (reader_id),
                INDEX idx_pr_used (used),
                CONSTRAINT fk_pr_reader FOREIGN KEY (reader_id) REFERENCES readers(id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS reminder_logs (
                id        INT AUTO_INCREMENT PRIMARY KEY,
                borrow_id INT NOT NULL,
                kind      VARCHAR(20) NOT NULL,
                sent_to   VARCHAR(120),
                ok        TINYINT NOT NULL DEFAULT 1,
                message   VARCHAR(255),
                sent_at   VARCHAR(30),
                INDEX idx_rem_borrow (borrow_id),
                INDEX idx_rem_kind (kind)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS operation_logs (
                id         INT AUTO_INCREMENT PRIMARY KEY,
                created_at VARCHAR(30),
                who        VARCHAR(60),
                role       VARCHAR(10),
                action     VARCHAR(40),
                target     VARCHAR(120),
                detail     VARCHAR(255),
                ip         VARCHAR(40),
                INDEX idx_log_time (created_at),
                INDEX idx_log_action (action),
                INDEX idx_log_role (role)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS book_reviews (
                id         INT AUTO_INCREMENT PRIMARY KEY,
                book_id    INT NOT NULL,
                reader_id  INT NOT NULL,
                rating     TINYINT NOT NULL DEFAULT 5,
                content    VARCHAR(500),
                hidden     TINYINT NOT NULL DEFAULT 0,
                created_at VARCHAR(30),
                updated_at VARCHAR(30),
                INDEX idx_rev_book (book_id),
                INDEX idx_rev_reader (reader_id),
                UNIQUE KEY uk_rev_book_reader (book_id, reader_id),
                CONSTRAINT fk_rev_book   FOREIGN KEY (book_id)   REFERENCES books(id),
                CONSTRAINT fk_rev_reader FOREIGN KEY (reader_id) REFERENCES readers(id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """, """
            CREATE TABLE IF NOT EXISTS borrow_requests (
                id         INT AUTO_INCREMENT PRIMARY KEY,
                book_id    INT NOT NULL,
                reader_id  INT NOT NULL,
                status     VARCHAR(20) NOT NULL DEFAULT '待办理',
                note       VARCHAR(200),
                created_at VARCHAR(30),
                done_at    VARCHAR(30),
                INDEX idx_req_book (book_id),
                INDEX idx_req_reader (reader_id),
                INDEX idx_req_status (status),
                CONSTRAINT fk_req_book   FOREIGN KEY (book_id)   REFERENCES books(id),
                CONSTRAINT fk_req_reader FOREIGN KEY (reader_id) REFERENCES readers(id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
            """);

    /** 老版本数据库补字段（已存在的列会跳过），保证从 Python 版升级不丢数据。 */
    private static final List<String> ENSURE_COLUMNS = List.of(
            "ALTER TABLE books ADD COLUMN cover VARCHAR(200)",
            "ALTER TABLE borrow_records ADD COLUMN fine_paid TINYINT NOT NULL DEFAULT 0",
            "ALTER TABLE borrow_records ADD COLUMN fine_paid_at VARCHAR(30)",
            "ALTER TABLE borrow_records ADD COLUMN fine_note VARCHAR(100)",
            "ALTER TABLE borrow_records ADD COLUMN fine_claim VARCHAR(20) NOT NULL DEFAULT ''",
            "ALTER TABLE borrow_records ADD COLUMN fine_claim_at VARCHAR(30)",
            "ALTER TABLE borrow_requests ADD COLUMN ready_at VARCHAR(30)",
            "ALTER TABLE borrow_requests ADD COLUMN notified TINYINT NOT NULL DEFAULT 0",
            "ALTER TABLE settings MODIFY COLUMN `value` TEXT");

    private static final List<String> WANTED_INDEXES = List.of(
            "CREATE INDEX idx_books_isbn ON books(isbn)",
            "CREATE INDEX idx_books_category ON books(category)",
            "CREATE INDEX idx_readers_phone ON readers(phone)",
            "CREATE INDEX idx_br_borrow_date ON borrow_records(borrow_date)");

    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("${spring.datasource.password}")
    private String password;

    private final JdbcTemplate jdbc;

    public DatabaseInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DatabaseInitializer.class);

    @Override
    public void run(ApplicationArguments args) {
        createDatabaseIfAbsent();
        for (String sql : SCHEMA) {
            jdbc.execute(sql);
        }
        ensureColumns();
        ensureIndexes();
        seedDefaultSettings();
        log.info("数据库初始化完成：建表 {} 张、补字段/索引 {} 项（已存在的自动跳过）",
                SCHEMA.size(), ENSURE_COLUMNS.size() + WANTED_INDEXES.size());
    }

    /** 先连服务器（不指定库）确保数据库存在；没有建库权限时只记日志，不阻断启动。 */
    private void createDatabaseIfAbsent() {
        String dbName = dbNameFromUrl(url);
        if (dbName.isBlank()) {
            log.warn("无法从 JDBC URL 解析出数据库名，跳过自动建库：{}", url);
            return;
        }
        try {
            DataSource server = new DriverManagerDataSource(serverUrlOf(url), username, password);
            new JdbcTemplate(server).execute("CREATE DATABASE IF NOT EXISTS `" + dbName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        } catch (Exception e) {
            // 没有建库权限不算错误（库通常已由平台建好），但必须记下来：
            // 早先这里把异常完全吞掉，"库其实没建成"就一直沉默，
            // 直到下一次启动失败才以 "Unknown database" 的形式暴露。
            log.warn("自动建库未完成（库可能已存在，或当前账号没有建库权限）：{}", e.getMessage());
        }
    }

    /**
     * 从 JDBC URL 里取出数据库名。
     *
     * <p>只能取 {@code jdbc:mysql://host:port/} 之后、第一个 {@code ?} 之前的那一段。
     *
     * <p>回归说明（真实踩过的坑）：早先的实现用的是 {@code url.lastIndexOf('/')}，
     * 而 URL 的查询参数里带着 {@code serverTimezone=Asia/Shanghai} —— 最后那个
     * 斜杠落在它身上，于是解析出来的"库名"是 {@code Shanghai&connectTimeout=8000}。
     * 后果有三层：一是建出一个名字是乱码的垃圾库；二是异常被吞掉、毫无提示；
     * 三是目标库从来没被建成，一旦它不是预先存在的，应用启动就直接失败。
     */
    static String dbNameFromUrl(String url) {
        int start = url.indexOf('/', "jdbc:mysql://".length());
        if (start < 0) {
            return "";
        }
        int end = url.indexOf('?', start);
        String name = end < 0 ? url.substring(start + 1) : url.substring(start + 1, end);
        return name.trim();
    }

    /** 把 JDBC URL 里的库名去掉，只留服务器地址，供"先连服务器建库"使用。 */
    static String serverUrlOf(String url) {
        int start = url.indexOf('/', "jdbc:mysql://".length());
        if (start < 0) {
            return url;
        }
        return url.substring(0, start)
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true";
    }

    private void ensureColumns() {
        for (String sql : ENSURE_COLUMNS) {
            try {
                jdbc.execute(sql);
            } catch (Exception e) {
                // 列已存在，跳过
            }
        }
    }

    private void ensureIndexes() {
        for (String sql : WANTED_INDEXES) {
            try {
                jdbc.execute(sql);
            } catch (Exception e) {
                // 索引已存在或表结构不满足，跳过
            }
        }
    }

    /** 写入默认设置和管理员密码（都存在才跳过，逐条 INSERT IGNORE）。 */
    private void seedDefaultSettings() {
        SettingsService.DEFAULT_SETTINGS.forEach((key, value) ->
                jdbc.update("INSERT IGNORE INTO settings(`key`, `value`) VALUES (?, ?)", key, value));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM settings WHERE `key` = 'admin_password_hash'", Integer.class);
        if (count == null || count == 0) {
            // 初始密码 123456，用与 Python 版 werkzeug 兼容的 pbkdf2 格式存储
            jdbc.update("INSERT INTO settings(`key`, `value`) VALUES (?, ?)",
                    "admin_password_hash", PasswordUtil.hash("123456"));
        }
    }
}
