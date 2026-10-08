package com.library.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.library.util.SqlDumpUtil;

/**
 * 数据备份：把全部数据导出成 .sql 文本（可用于恢复），存放在项目 backups 目录。
 * 导出内容是 DELETE + INSERT 语句，配合 SqlDumpUtil 的转义规则，
 * 生成的 .sql 文件可以用 MySQL 客户端直接执行来恢复数据。
 */
@Service
public class BackupService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> TABLES = List.of(
            "books", "readers", "borrow_records", "settings");

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final LogService logService;
    private final Path backupDir;

    public BackupService(JdbcTemplate jdbc, SettingsService settings, LogService logService,
                         @Value("${library.backup-dir}") String backupDir) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.logService = logService;
        this.backupDir = Path.of(backupDir);
    }

    /** 生成完整备份的 SQL 文本内容。 */
    public String backupSql() {
        String dbName = settings.get("library_name");
        StringBuilder sb = new StringBuilder();
        sb.append("-- 图书管理系统数据备份  ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n-- 数据库：").append(dbName)
                .append("\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n\n");
        for (String table : TABLES) {
            List<MapResult> rows = queryMap(table);
            // 用 DELETE 而不是 TRUNCATE：恢复失败时事务还能回滚
            sb.append("DELETE FROM `").append(table).append("`;\n");
            for (MapResult row : rows) {
                StringBuilder cols = new StringBuilder();
                StringBuilder values = new StringBuilder();
                for (int i = 0; i < row.columns.size(); i++) {
                    if (i > 0) {
                        cols.append(", ");
                        values.append(", ");
                    }
                    cols.append('`').append(row.columns.get(i)).append('`');
                    values.append(SqlDumpUtil.quoteSql(row.values.get(i)));
                }
                sb.append("INSERT INTO `").append(table).append("` (").append(cols)
                        .append(") VALUES (").append(values).append(");\n");
            }
            sb.append('\n');
        }
        sb.append("SET FOREIGN_KEY_CHECKS=1;\n");
        return sb.toString();
    }

    private record MapResult(List<String> columns, List<Object> values) {
    }

    private List<MapResult> queryMap(String table) {
        List<MapResult> result = new ArrayList<>();
        jdbc.query("SELECT * FROM " + table, rs -> {
            int n = rs.getMetaData().getColumnCount();
            List<String> cols = new ArrayList<>(n);
            List<Object> vals = new ArrayList<>(n);
            for (int i = 1; i <= n; i++) {
                cols.add(rs.getMetaData().getColumnLabel(i));
                vals.add(rs.getObject(i));
            }
            result.add(new MapResult(cols, vals));
        });
        return result;
    }

    /** 立即备份一份到 backups 目录，返回文件名。 */
    public String makeBackup(String tag) {
        try {
            Files.createDirectories(backupDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建备份目录：" + backupDir, e);
        }
        String name = tag + "-" + LocalDateTime.now().format(STAMP) + ".sql";
        try {
            Files.writeString(backupDir.resolve(name), backupSql(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("备份文件写入失败", e);
        }
        return name;
    }

    /** 备份文件列表（新的在前）。 */
    public record BackupFile(String name, double sizeKb, String time, long mtime) {
    }

    public List<BackupFile> listBackups(int limit) {
        if (!Files.isDirectory(backupDir)) {
            return List.of();
        }
        List<BackupFile> items = new ArrayList<>();
        try (Stream<Path> files = Files.list(backupDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".sql")).forEach(p -> {
                try {
                    var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
                    String name = p.getFileName().toString();
                    items.add(new BackupFile(name,
                            Math.round(attrs.size() / 102.4) / 10.0,
                            LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(),
                                    java.time.ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            attrs.lastModifiedTime().toMillis()));
                } catch (IOException e) {
                    // 单个文件读不到就跳过
                }
            });
        } catch (IOException e) {
            return List.of();
        }
        items.sort(Comparator.comparingLong(BackupFile::mtime).reversed());
        return items.size() > limit ? items.subList(0, limit) : items;
    }

    /** 删除太旧的备份，只保留最近 keepDays 天。返回删除个数。 */
    public int cleanupOldBackups(int keepDays) {
        if (keepDays <= 0) {
            return 0;
        }
        long deadline = System.currentTimeMillis() - keepDays * 86_400_000L;
        int removed = 0;
        try (Stream<Path> files = Files.list(backupDir)) {
            for (Path p : files.filter(p -> p.getFileName().toString().endsWith(".sql")).toList()) {
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < deadline) {
                        Files.deleteIfExists(p);
                        removed++;
                    }
                } catch (IOException e) {
                    // 单个删除失败不影响其余
                }
            }
        } catch (IOException e) {
            return removed;
        }
        return removed;
    }

    /** 今天是否已经自动备份过。 */
    public boolean todayBackupExists() {
        String prefix = "自动备份-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return listBackups(1000).stream().anyMatch(b -> b.name().startsWith(prefix));
    }

    /** 每日自动备份任务体：开关开着且今天没备过才备份。返回备份文件名或 null。 */
    public String runDailyBackupIfNeeded() {
        if (!settings.getBool("auto_backup_enabled")) {
            return null;
        }
        if (todayBackupExists()) {
            return null;
        }
        String name = makeBackup("自动备份");
        int removed = cleanupOldBackups(settings.getInt("auto_backup_keep_days", 30));
        logService.action("每日自动备份", name,
                removed > 0 ? "清理旧备份 " + removed + " 个" : "", "系统", "系统", "");
        return name;
    }

    /** 备份文件的安全路径：只允许文件名本身（防 ../ 越权访问）。 */
    public Path resolveBackup(String name) {
        if (name == null) {
            return null;
        }
        String safe = Path.of(name).getFileName().toString();
        if (!safe.endsWith(".sql")) {
            return null;
        }
        Path path = backupDir.resolve(safe).normalize();
        return path.startsWith(backupDir) ? path : null;
    }

    public Path getBackupDir() {
        return backupDir;
    }
}
