package com.library.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.library.dao.AuxDao;
import com.library.dao.BorrowDao;
import com.library.entity.BorrowRecord;
import com.library.entity.PasswordReset;
import com.library.service.BorrowService;
import com.library.service.SettingsService;

/**
 * 工作台（首页）：库存总览、逾期列表、即将到期、最近借阅、密码重置提醒。
 */
@Controller
public class DashboardController {

    private final BorrowDao borrowDao;
    private final AuxDao auxDao;
    private final BorrowService borrowService;
    private final SettingsService settings;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public DashboardController(BorrowDao borrowDao, AuxDao auxDao,
                               BorrowService borrowService, SettingsService settings,
                               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.borrowDao = borrowDao;
        this.auxDao = auxDao;
        this.borrowService = borrowService;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("bookKinds", scalar("SELECT COUNT(*) FROM books WHERE is_deleted = 0"));
        stats.put("bookCopies", scalar("SELECT COALESCE(SUM(total_copies),0) FROM books WHERE is_deleted = 0"));
        stats.put("available", scalar("SELECT COALESCE(SUM(available_copies),0) FROM books WHERE is_deleted = 0"));
        stats.put("readers", scalar("SELECT COUNT(*) FROM readers WHERE is_deleted = 0"));
        stats.put("borrowed", scalar("SELECT COUNT(*) FROM borrow_records WHERE return_date IS NULL"));

        List<BorrowRecord> overdue = borrowDao.query("""
                SELECT br.*, b.title AS book_title, r.name AS reader_name, r.card_no, r.phone
                FROM borrow_records br
                JOIN books b   ON b.id = br.book_id
                JOIN readers r ON r.id = br.reader_id
                WHERE br.return_date IS NULL AND br.due_date < ?
                ORDER BY br.due_date ASC
                """, LocalDate.now().toString());
        borrowService.decorate(overdue);
        double overdueFine = overdue.stream().mapToDouble(BorrowRecord::getFine).sum();
        stats.put("overdue", overdue.size());
        stats.put("overdueFine", Math.round(overdueFine * 100.0) / 100.0);

        List<BorrowRecord> dueSoon = borrowDao.query("""
                SELECT br.*, b.title AS book_title, r.name AS reader_name, r.phone
                FROM borrow_records br
                JOIN books b   ON b.id = br.book_id
                JOIN readers r ON r.id = br.reader_id
                WHERE br.return_date IS NULL AND br.due_date >= ?
                ORDER BY br.due_date ASC LIMIT 6
                """, LocalDate.now().toString());
        borrowService.decorate(dueSoon);

        List<BorrowRecord> recent = borrowDao.query("""
                SELECT br.*, b.title AS book_title, r.name AS reader_name
                FROM borrow_records br
                JOIN books b   ON b.id = br.book_id
                JOIN readers r ON r.id = br.reader_id
                ORDER BY br.borrow_date DESC, br.id DESC LIMIT 8
                """);
        borrowService.decorate(recent);

        List<PasswordReset> pendingResets = auxDao.recentResets();

        model.addAttribute("stats", stats);
        model.addAttribute("overdue", overdue);
        model.addAttribute("dueSoon", dueSoon);
        model.addAttribute("recent", recent);
        model.addAttribute("pendingResets", pendingResets);
        model.addAttribute("resetNow", com.library.util.DateUtil.nowStr());
        model.addAttribute("active", "dashboard");
        return "dashboard";
    }

    private long scalar(String sql) {
        Long v = jdbc.queryForObject(sql, Long.class);
        return v == null ? 0 : v;
    }
}
