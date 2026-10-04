package com.library.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.library.dao.BookDao;
import com.library.dao.BorrowDao;
import com.library.entity.BorrowRecord;
import com.library.service.BorrowService;
import com.library.service.SettingsService;

/** 打印：借书单、催还单、书标（打印友好页面，浏览器直接打印）。 */
@Controller
public class PrintController {

    private final BorrowDao borrowDao;
    private final BookDao bookDao;
    private final BorrowService borrowService;
    private final SettingsService settings;

    public PrintController(BorrowDao borrowDao, BookDao bookDao,
                           BorrowService borrowService, SettingsService settings) {
        this.borrowDao = borrowDao;
        this.bookDao = bookDao;
        this.borrowService = borrowService;
        this.settings = settings;
    }

    /** 借书单（办完借书可以打一张给读者）。 */
    @GetMapping("/print/receipt/{recordId}")
    public String receipt(@PathVariable Integer recordId, Model model) {
        BorrowRecord record = borrowDao.findById(recordId);
        if (record == null) {
            return "redirect:/borrows";
        }
        model.addAttribute("r", record);
        model.addAttribute("printedAt", LocalDate.now().toString());
        return "print_receipt";
    }

    /** 催还单：逾期未还的书按读者列出，打印后打电话催。 */
    @GetMapping("/print/overdue")
    public String overdueList(Model model) {
        List<BorrowRecord> records = borrowDao.query("""
                SELECT br.*, b.title AS book_title, r.name AS reader_name, r.card_no, r.phone,
                       r.department
                FROM borrow_records br
                JOIN books b   ON b.id = br.book_id
                JOIN readers r ON r.id = br.reader_id
                WHERE br.return_date IS NULL AND br.due_date < ?
                ORDER BY r.name, br.due_date
                """, LocalDate.now().toString());
        borrowService.decorate(records);
        double totalFine = records.stream().mapToDouble(BorrowRecord::getFine).sum();
        model.addAttribute("records", records);
        model.addAttribute("printedAt", LocalDate.now().toString());
        model.addAttribute("totalFine", Math.round(totalFine * 100.0) / 100.0);
        return "print_overdue";
    }

    /** 书标：打印出来剪开贴到书上（书名、作者、位置、书号）。 */
    @GetMapping("/print/labels")
    public String labels(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("books", bookDao.searchForLabels(q, 200));
        model.addAttribute("keyword", q == null ? "" : q);
        model.addAttribute("printedAt", LocalDate.now().toString());
        return "print_labels";
    }
}
