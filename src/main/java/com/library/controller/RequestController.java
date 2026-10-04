package com.library.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.dao.BookDao;
import com.library.dao.BorrowDao;
import com.library.dao.BorrowRequestDao;
import com.library.entity.Book;
import com.library.entity.BorrowRequest;
import com.library.service.LogService;
import com.library.service.SettingsService;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;

import static com.library.controller.BookController.param;

/**
 * 借阅申请（排队）：读者在线提交申请 → 管理员在后台看到待办 → 办理借出时自动标记。
 * 申请期间不占用可借册数；超过设定天数未办理自动取消。
 */
@Controller
public class RequestController {

    private final BorrowRequestDao requestDao;
    private final BorrowDao borrowDao;
    private final BookDao bookDao;
    private final SettingsService settings;
    private final LogService logService;
    private final com.library.service.LoginContext loginContext;

    public RequestController(BorrowRequestDao requestDao, BorrowDao borrowDao, BookDao bookDao,
                             SettingsService settings, LogService logService,
                             com.library.service.LoginContext loginContext) {
        this.requestDao = requestDao;
        this.borrowDao = borrowDao;
        this.bookDao = bookDao;
        this.settings = settings;
        this.logService = logService;
        this.loginContext = loginContext;
    }

    // ---------------- 读者端 ----------------

    /** 读者提交借阅申请。 */
    @PostMapping("/reader/request/{bookId}")
    public String readerRequest(@PathVariable Integer bookId,
                                HttpServletRequest request,
                                RedirectAttributes ra) {
        var reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        if (!"1".equals(settings.get("borrow_need_approval"))) {
            Flash.warn(ra, "现在不需要申请，直接点「借这本书」就行。");
            return "redirect:/reader/";
        }
        Book book = bookDao.findById(bookId);
        if (book == null || book.getIsDeleted() == 1) {
            Flash.error(ra, "这本书已经下架了。");
            return "redirect:/reader/";
        }
        if (borrowDao.isCurrentlyBorrowing(reader.getId(), bookId)) {
            Flash.error(ra, "你已经借着《" + book.getTitle() + "》还没还，不用再申请。");
            return "redirect:/reader/";
        }
        if (requestDao.existsPending(reader.getId(), bookId)) {
            Flash.warn(ra, "你已经申请过这本书了，等管理员办理就行。");
            return "redirect:/reader/my";
        }
        String note = param(request, "note");
        if (note.length() > 200) {
            note = note.substring(0, 200);
        }
        requestDao.insert(bookId, reader.getId(), note);
        logService.action("提交借阅申请", "《" + book.getTitle() + "》", reader.getName());
        if (book.getAvailableCopies() != null && book.getAvailableCopies() > 0) {
            Flash.ok(ra, "已提交《" + book.getTitle() + "》的借阅申请。请到图书室取书时找管理员办理，"
                    + "管理员确认后就正式借出。");
        } else {
            Flash.ok(ra, "《" + book.getTitle() + "》现在都借出去了，已为你预约排队。"
                    + "有书还回来时会通知你，请留意「我的借阅」。");
        }
        return "redirect:/reader/my";
    }

    /** 读者取消自己的申请。 */
    @PostMapping("/reader/request/{requestId}/cancel")
    public String readerCancel(@PathVariable Integer requestId, RedirectAttributes ra) {
        var reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        BorrowRequest row = requestDao.findByIdAndReader(requestId, reader.getId());
        if (row == null) {
            return "redirect:/reader/my";
        }
        requestDao.cancel(requestId);
        Flash.ok(ra, "申请已取消。");
        return "redirect:/reader/my";
    }

    // ---------------- 管理端 ----------------

    /** 借阅申请列表（进入时先做过期自动取消）。 */
    @GetMapping("/requests")
    public String list(@RequestParam(required = false) String status, Model model) {
        requestDao.autoCancelExpired(requestExpireDays());
        List<BorrowRequest> rows = requestDao.list(status);
        int[] counts = requestDao.counts();
        model.addAttribute("rows", rows);
        model.addAttribute("countAll", counts[0]);
        model.addAttribute("countPending", counts[1]);
        model.addAttribute("countReady", counts[2]);
        model.addAttribute("countDone", counts[3]);
        model.addAttribute("countCancelled", counts[4]);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("active", "requests");
        return "requests";
    }

    /** 管理端取消（拒绝）申请；办理借出走 /borrow/new，成功后自动标记。 */
    @PostMapping("/requests/{requestId}/cancel")
    public String cancel(@PathVariable Integer requestId, HttpServletRequest request, RedirectAttributes ra) {
        BorrowRequest row = requestDao.findById(requestId);
        if (row == null) {
            return "redirect:/requests";
        }
        requestDao.cancel(requestId);
        Flash.ok(ra, "已取消 " + row.getReaderName() + " 对《" + row.getBookTitle() + "》的申请。");
        logService.action("取消借阅申请", row.getReaderName() + "《" + row.getBookTitle() + "》");
        String backStatus = request.getParameter("back_status");
        return "redirect:/requests" + (backStatus != null && !backStatus.isBlank()
                ? "?status=" + backStatus : "");
    }

    private int requestExpireDays() {
        return settings.getInt("request_expire_days", 3);
    }
}
