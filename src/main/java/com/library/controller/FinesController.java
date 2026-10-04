package com.library.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.dao.BorrowDao;
import com.library.entity.BorrowRecord;
import com.library.entity.Reader;
import com.library.service.BorrowService;
import com.library.service.LogService;
import com.library.service.SettingsService;
import com.library.util.DateUtil;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.RequestParam;

import static com.library.controller.BookController.param;

/** 罚金：读者端缴费页/缴费申请 + 管理端台账/收款标记/减免。 */
@Controller
public class FinesController {

    private final BorrowDao borrowDao;
    private final BorrowService borrowService;
    private final SettingsService settings;
    private final LogService logService;
    private final com.library.service.LoginContext loginContext;

    public FinesController(BorrowDao borrowDao, BorrowService borrowService,
                           SettingsService settings, LogService logService,
                           com.library.service.LoginContext loginContext) {
        this.borrowDao = borrowDao;
        this.borrowService = borrowService;
        this.settings = settings;
        this.logService = logService;
        this.loginContext = loginContext;
    }

    // ---------------- 读者端 ----------------

    /** 读者缴纳罚金页面：显示金额 + 收款码 + 说明。 */
    @GetMapping("/reader/fine/{recordId}/pay")
    public String readerPay(@PathVariable Integer recordId, Model model) {
        Reader reader = currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        BorrowRecord row = borrowDao.findByIdAndReader(recordId, reader.getId());
        if (row == null) {
            return "redirect:/reader/my";
        }
        double fine;
        if (row.getReturnDate() != null) {
            fine = row.getFineAmount() == null ? 0 : row.getFineAmount();
        } else {
            LocalDate due = DateUtil.parseDate(row.getDueDate());
            int overdueDays = due == null ? 0
                    : Math.max(0, (int) (LocalDate.now().toEpochDay() - due.toEpochDay()));
            fine = Math.round(overdueDays * settings.getDouble("fine_per_day") * 100.0) / 100.0;
        }
        model.addAttribute("r", row);
        model.addAttribute("fine", fine);
        model.addAttribute("reader", reader);
        model.addAttribute("active", "my");
        return "reader_pay";
    }

    /** 读者点“我已缴纳”：提交申请，管理员核对到账后确认。 */
    @PostMapping("/reader/fine/{recordId}/claim")
    public String readerClaim(@PathVariable Integer recordId, RedirectAttributes ra) {
        Reader reader = currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        BorrowRecord row = borrowDao.findByIdAndReader(recordId, reader.getId());
        if (row == null) {
            return "redirect:/reader/my";
        }
        if (row.getFinePaid() == 1) {
            Flash.ok(ra, "这笔罚金已经确认收过了。");
            return "redirect:/reader/my";
        }
        borrowDao.claimFine(recordId, DateUtil.nowStr());
        logService.action("读者申请缴纳罚金", reader.getName(),
                String.format("%.2f", row.getFineAmount()) + " 元");
        Flash.ok(ra, "已提交缴纳申请。管理员核对到账后会确认，你可以在「我的借阅」里看到状态。");
        return "redirect:/reader/my";
    }

    // ---------------- 管理端 ----------------

    /** 罚金台账：所有归还时产生过罚金的记录（可按已收/未收筛选）。 */
    @GetMapping("/fines")
    public String fineList(@RequestParam(required = false) String status, Model model) {
        List<BorrowRecord> records = borrowDao.fineLedger();
        borrowService.decorate(records);
        if ("unpaid".equals(status)) {
            records = records.stream().filter(r -> r.getFinePaid() == 0).toList();
        } else if ("paid".equals(status)) {
            records = records.stream().filter(r -> r.getFinePaid() == 1).toList();
        }
        double total = records.stream().mapToDouble(BorrowRecord::getFine).sum();
        double paid = records.stream().filter(r -> r.getFinePaid() == 1)
                .mapToDouble(BorrowRecord::getFine).sum();
        model.addAttribute("records", records);
        model.addAttribute("stats", java.util.Map.of(
                "count", records.size(),
                "total", Math.round(total * 100.0) / 100.0,
                "paid", Math.round(paid * 100.0) / 100.0,
                "unpaid", Math.round((total - paid) * 100.0) / 100.0));
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("active", "fines");
        return "fines";
    }

    /** 标记罚金已收 / 减免 / 改回未收。 */
    @PostMapping("/fines/{recordId}/mark")
    public String mark(@PathVariable Integer recordId, HttpServletRequest request, RedirectAttributes ra) {
        String action = param(request, "action");
        String note = param(request, "fine_note");
        BorrowRecord record = borrowDao.findById(recordId);
        if (record == null) {
            return "redirect:/fines";
        }
        if ("paid".equals(action)) {
            borrowDao.markFinePaid(recordId, DateUtil.nowStr(), note);
            Flash.ok(ra, String.format("已记录：%s 的罚金 %.2f 元已收。",
                    record.getReaderName(), record.getFineAmount()));
            logService.action("罚金已收", record.getReaderName() + "《" + record.getBookTitle() + "》",
                    String.format("%.2f", record.getFineAmount()) + " 元");
        } else if ("waive".equals(action)) {
            borrowDao.waiveFine(recordId, DateUtil.nowStr(), note.isEmpty() ? "已减免" : note);
            Flash.ok(ra, "已把 " + record.getReaderName() + " 的这笔罚金减免为 0 元。");
            logService.action("罚金减免", record.getReaderName() + "《" + record.getBookTitle() + "》",
                    String.format("原 %.2f 元", record.getFineAmount()));
        } else if ("unpaid".equals(action)) {
            borrowDao.unmarkFinePaid(recordId, note.isEmpty() ? "取消收款标记" : note);
            Flash.ok(ra, "已改回「未收」。");
        }
        String backStatus = request.getParameter("back_status");
        return "redirect:/fines" + (backStatus != null && !backStatus.isBlank() ? "?status=" + backStatus : "");
    }

    private Reader currentReader() {
        return loginContext.currentReader();
    }
}
