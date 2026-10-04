package com.library.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.library.dao.BorrowDao;
import com.library.entity.BorrowRecord;
import com.library.service.BackupService;
import com.library.service.BorrowService;
import com.library.service.LogService;
import com.library.util.CsvUtil;

/** 导出：图书/读者/借阅 CSV + 完整 .sql 备份下载。文件名带日期，Excel 打开不乱码。 */
@Controller
public class ExportController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter STAMP_MIN = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final com.library.dao.BookDao bookDao;
    private final com.library.dao.ReaderDao readerDao;
    private final BorrowDao borrowDao;
    private final BorrowService borrowService;
    private final BackupService backupService;
    private final LogService logService;

    public ExportController(com.library.dao.BookDao bookDao, com.library.dao.ReaderDao readerDao,
                            BorrowDao borrowDao, BorrowService borrowService,
                            BackupService backupService, LogService logService) {
        this.bookDao = bookDao;
        this.readerDao = readerDao;
        this.borrowDao = borrowDao;
        this.borrowService = borrowService;
        this.backupService = backupService;
        this.logService = logService;
    }

    /** CSV 下载响应体（带 BOM 与 UTF-8 文件名）。 */
    private ResponseEntity<byte[]> csv(String filename, List<String> header, List<List<String>> rows) {
        byte[] body = CsvUtil.buildCsv(header, rows);
        String stamp = LocalDateTime.now().format(STAMP);
        String encoded = URLEncoder.encode(filename + "-" + stamp + ".csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition",
                        "attachment; filename=library-" + stamp + ".csv; filename*=UTF-8''" + encoded)
                .body(body);
    }

    @GetMapping("/export/books.csv")
    public ResponseEntity<byte[]> books() {
        List<List<String>> rows = new ArrayList<>();
        for (var b : bookDao.listAll("SELECT * FROM books WHERE is_deleted = 0 ORDER BY id")) {
            rows.add(List.of(String.valueOf(b.getId()), nullSafe(b.getTitle()), nullSafe(b.getAuthor()),
                    nullSafe(b.getCategory()), nullSafe(b.getIsbn()), nullSafe(b.getPublisher()),
                    b.getPublishYear() == null ? "" : b.getPublishYear().toString(),
                    b.getPrice() == null ? "" : b.getPrice().toString(),
                    nullSafe(b.getLocation()), String.valueOf(b.getTotalCopies()),
                    String.valueOf(b.getAvailableCopies()), nullSafe(b.getSummary()),
                    nullSafe(b.getCreatedAt())));
        }
        return csv("图书清单",
                List.of("编号", "书名", "作者", "分类", "ISBN", "出版社", "出版年份", "单价",
                        "馆藏位置", "总册数", "可借册数", "简介", "录入时间"), rows);
    }

    @GetMapping("/export/readers.csv")
    public ResponseEntity<byte[]> readers() {
        List<List<String>> rows = new ArrayList<>();
        for (var r : readerDao.listAll("SELECT * FROM readers WHERE is_deleted = 0 ORDER BY id")) {
            rows.add(List.of(String.valueOf(r.getId()), nullSafe(r.getCardNo()), nullSafe(r.getName()),
                    nullSafe(r.getGender()), nullSafe(r.getReaderType()), nullSafe(r.getDepartment()),
                    nullSafe(r.getPhone()), nullSafe(r.getStatus()), nullSafe(r.getCreatedAt())));
        }
        return csv("读者清单",
                List.of("编号", "借书卡号", "姓名", "性别", "读者类型", "单位/班级",
                        "联系电话", "状态", "登记时间"), rows);
    }

    @GetMapping("/export/borrows.csv")
    public ResponseEntity<byte[]> borrows() {
        List<BorrowRecord> records = borrowDao.query("""
                SELECT br.*, b.title AS book_title, b.isbn AS book_isbn,
                       r.name AS reader_name, r.card_no
                FROM borrow_records br
                JOIN books b   ON b.id = br.book_id
                JOIN readers r ON r.id = br.reader_id
                ORDER BY br.borrow_date DESC, br.id DESC
                """);
        borrowService.decorate(records);
        List<List<String>> rows = new ArrayList<>();
        for (BorrowRecord r : records) {
            rows.add(List.of(String.valueOf(r.getId()), nullSafe(r.getReaderName()), nullSafe(r.getCardNo()),
                    nullSafe(r.getBookTitle()), nullSafe(r.getBookIsbn()), nullSafe(r.getBorrowDate()),
                    nullSafe(r.getDueDate()), nullSafe(r.getReturnDate()), String.valueOf(r.getRenewCount()),
                    nullSafe(r.getStatus()), String.valueOf(r.getOverdueDays()),
                    String.format("%.2f", r.getFine()), nullSafe(r.getRemark())));
        }
        return csv("借阅记录",
                List.of("编号", "读者", "借书卡号", "书名", "ISBN", "借出日期", "应还日期",
                        "归还日期", "续借次数", "状态", "逾期天数", "罚金(元)", "备注"), rows);
    }

    @GetMapping("/export/backup.sql")
    public ResponseEntity<byte[]> backup() {
        String stamp = LocalDateTime.now().format(STAMP_MIN);
        logService.action("导出完整备份", "library-backup-" + stamp + ".sql");
        String encoded = URLEncoder.encode("图书管理数据备份-" + stamp + ".sql", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Type", "application/sql; charset=utf-8")
                .header("Content-Disposition",
                        "attachment; filename=library-backup-" + stamp + ".sql; "
                                + "filename*=UTF-8''" + encoded)
                .body(backupService.backupSql().getBytes(StandardCharsets.UTF_8));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
