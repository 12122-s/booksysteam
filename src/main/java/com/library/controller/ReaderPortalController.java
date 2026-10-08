package com.library.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.library.dao.ReaderDao;
import com.library.dao.ReviewDao;
import com.library.entity.Book;
import com.library.entity.BorrowRecord;
import com.library.entity.Reader;
import com.library.service.BorrowService;
import com.library.service.LoginContext;
import com.library.service.RecommendService;
import com.library.service.SettingsService;
import com.library.util.PasswordUtil;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import static com.library.controller.BookController.param;

/** 读者端：找书 / 我的借阅 / 自助借书 / 续借 / 个人资料。 */
@Controller
public class ReaderPortalController {

    private final BookDao bookDao;
    private final BorrowDao borrowDao;
    private final BorrowRequestDao requestDao;
    private final ReaderDao readerDao;
    private final ReviewDao reviewDao;
    private final BorrowService borrowService;
    private final RecommendService recommendService;
    private final SettingsService settings;
    private final LoginContext loginContext;

    public ReaderPortalController(BookDao bookDao, BorrowDao borrowDao,
                                  BorrowRequestDao requestDao, ReaderDao readerDao,
                                  ReviewDao reviewDao, BorrowService borrowService,
                                  RecommendService recommendService,
                                  SettingsService settings, LoginContext loginContext) {
        this.bookDao = bookDao;
        this.borrowDao = borrowDao;
        this.requestDao = requestDao;
        this.readerDao = readerDao;
        this.reviewDao = reviewDao;
        this.borrowService = borrowService;
        this.recommendService = recommendService;
        this.settings = settings;
        this.loginContext = loginContext;
    }

    /** 读者书城：搜索/分类/只看可借 + 每本书的评分摘要 + “猜你可能想看”。
     *  读者端首页同时映射 /reader 与 /reader/（带尾斜杠）两种写法。 */
    @GetMapping({"/reader", "/reader/"})
    public String readerBooks(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String category,
                              @RequestParam(name = "only_available", required = false) String onlyAvailable,
                              @RequestParam(defaultValue = "1") Integer page,
                              Model model) {
        Reader reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        int borrowDays = settings.getInt("borrow_days");
        int maxBooks = settings.getInt("max_books_per_reader");
        boolean onlyAvail = "1".equals(onlyAvailable);

        BookDao.ListResult<Book> result = bookDao.list(q, category, onlyAvail, page, 15);
        // 读者端排序：有库存的排前面，再按书名
        List<Book> books = new ArrayList<>(result.items());
        books.sort((a, b) -> {
            int avail = Integer.compare(
                    b.getAvailableCopies() != null && b.getAvailableCopies() > 0 ? 1 : 0,
                    a.getAvailableCopies() != null && a.getAvailableCopies() > 0 ? 1 : 0);
            if (avail != 0) {
                return avail;
            }
            return String.valueOf(a.getTitle()).compareTo(String.valueOf(b.getTitle()));
        });

        Set<Integer> myBookIds = new HashSet<>();
        for (BorrowRecord r : borrowDao.activeByReader(reader.getId())) {
            myBookIds.add(r.getBookId());
        }

        Map<Integer, ReviewDao.ReviewStats> reviewStats = reviewDao.statsAll();
        Map<Integer, List<com.library.entity.BookReview>> reviewLatest = reviewDao.latestMap(3);

        List<BorrowRecord> myActive = borrowDao.activeByReader(reader.getId());
        borrowService.decorate(myActive);
        int borrowing = readerDao.borrowingCount(reader.getId());
        List<RecommendService.Recommendation> recommends =
                recommendService.recommendFor(reader.getId(), 6);

        model.addAttribute("reader", reader);
        model.addAttribute("books", books);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("categories", bookDao.categories());
        model.addAttribute("onlyAvailable", onlyAvail);
        model.addAttribute("page", result.page());
        model.addAttribute("pages", result.pages());
        model.addAttribute("total", result.total());
        model.addAttribute("myBookIds", myBookIds);
        model.addAttribute("recommends", recommends);
        model.addAttribute("reviewStats", reviewStats);
        model.addAttribute("reviewLatest", reviewLatest);
        model.addAttribute("needApproval", settings.getBool("borrow_need_approval"));
        model.addAttribute("borrowing", borrowing);
        model.addAttribute("maxBooks", maxBooks);
        model.addAttribute("borrowDays", borrowDays);
        model.addAttribute("myActive", myActive);
        model.addAttribute("allowSelfBorrow", settings.getBool("allow_self_borrow"));
        model.addAttribute("active", "books");
        return "reader_books";
    }

    /** 读者自助借书（原子扣库存防超借）。 */
    @PostMapping("/reader/borrow/{bookId}")
    public String readerBorrow(@PathVariable Integer bookId, RedirectAttributes ra) {
        Reader reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        if (!settings.getBool("allow_self_borrow")) {
            Flash.error(ra, "图书室暂时关闭了在线借书，请到前台找管理员办理。");
            return "redirect:/reader/";
        }
        if (!"正常".equals(reader.getStatus())) {
            Flash.error(ra, "你的账号暂时不能借书，请联系管理员。");
            return "redirect:/reader/";
        }
        BorrowService.OpResult result = borrowService.readerSelfBorrow(bookId, reader.getId());
        if (result.ok()) {
            Flash.ok(ra, result.message());
        } else {
            Flash.error(ra, result.message());
        }
        return "redirect:/reader/my";
    }

    /** 我的借阅：在借/历史/未缴罚金/我的申请。 */
    @GetMapping("/reader/my")
    public String readerMy(Model model) {
        Reader reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        List<BorrowRecord> records = borrowDao.allByReader(reader.getId());
        borrowService.decorate(records);
        List<BorrowRecord> active = records.stream()
                .filter(r -> !"已归还".equals(r.getStatus())).toList();
        List<BorrowRecord> history = records.stream()
                .filter(r -> "已归还".equals(r.getStatus())).toList();
        double unpaid = records.stream()
                .filter(r -> r.getFine() > 0 && r.getFinePaid() != 1)
                .mapToDouble(BorrowRecord::getFine).sum();
        long overdueNow = active.stream().filter(r -> "已逾期".equals(r.getStatus())).count();
        List<BorrowRecord> myUnpaidFines = records.stream()
                .filter(r -> r.getFine() > 0 && r.getFinePaid() != 1).toList();
        List<com.library.entity.BorrowRequest> myRequests = requestDao.pendingByReader(reader.getId());

        model.addAttribute("reader", reader);
        model.addAttribute("activeRecords", active);
        model.addAttribute("history", history);
        model.addAttribute("unpaid", Math.round(unpaid * 100.0) / 100.0);
        model.addAttribute("overdueNow", overdueNow);
        model.addAttribute("myRequests", myRequests);
        model.addAttribute("myUnpaidFines", myUnpaidFines);
        model.addAttribute("maxBooks", settings.getInt("max_books_per_reader"));
        model.addAttribute("maxRenew", settings.getInt("max_renew_times"));
        model.addAttribute("borrowDays", settings.getInt("borrow_days"));
        model.addAttribute("active", "my");
        return "reader_my";
    }

    /** 读者续借自己的书。 */
    @PostMapping("/reader/renew/{recordId}")
    public String readerRenew(@PathVariable Integer recordId,
                              HttpServletRequest request, RedirectAttributes ra) {
        Reader reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        Integer days = null;
        String raw = request.getParameter("days");
        if (raw != null && !raw.isBlank()) {
            try {
                days = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                days = null;
            }
        }
        BorrowService.OpResult result = borrowService.renew(recordId, days, reader.getId());
        if (result == null) {
            return "redirect:/reader/my";
        }
        if (result.ok()) {
            Flash.ok(ra, result.message());
        } else {
            Flash.error(ra, result.message());
        }
        return "redirect:/reader/my";
    }

    @GetMapping("/reader/profile")
    public String profile(Model model) {
        Reader reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        int total = readerDao.borrowHistoryCount(reader.getId());
        model.addAttribute("reader", reader);
        model.addAttribute("total", total);
        model.addAttribute("borrowing", readerDao.borrowingCount(reader.getId()));
        model.addAttribute("maxBooks", settings.getInt("max_books_per_reader"));
        boolean webEmailOn = notBlank(settings.get("smtp_host"))
                && notBlank(settings.get("smtp_user")) && notBlank(settings.get("smtp_pass"));
        model.addAttribute("webEmailOn", webEmailOn);
        model.addAttribute("active", "profile");
        return "reader_profile";
    }

    @PostMapping("/reader/password")
    public String changePassword(HttpServletRequest request, RedirectAttributes ra) {
        Reader reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        String current = request.getParameter("current_password");
        String newPw = request.getParameter("new_password");
        String confirm = request.getParameter("confirm_password");
        if (reader.getPasswordHash() == null || !PasswordUtil.check(reader.getPasswordHash(), current)) {
            Flash.error(ra, "当前密码不正确，密码没有修改。");
        } else if (newPw == null || newPw.length() < 6) {
            Flash.error(ra, "新密码至少要 6 位。");
        } else if (!newPw.equals(confirm)) {
            Flash.error(ra, "两次输入的新密码不一样，请重新输入。");
        } else {
            readerDao.updatePassword(reader.getId(), PasswordUtil.hash(newPw));
            Flash.ok(ra, "密码修改成功，下次登录请用新密码。");
        }
        return "redirect:/reader/profile";
    }

    /** 读者自己填写/修改邮箱（找回密码收验证码用）。 */
    @PostMapping("/reader/email")
    public String updateEmail(HttpServletRequest request, RedirectAttributes ra) {
        Reader reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        String email = param(request, "email");
        boolean valid = email.isEmpty()
                || (email.contains("@") && email.split("@", 2)[1].contains("."));
        if (!valid) {
            Flash.error(ra, "邮箱格式不对，正确的样子是 yourname@qq.com。");
        } else {
            readerDao.updateEmail(reader.getId(), email);
            Flash.ok(ra, email.isEmpty() ? "已清空邮箱。"
                    : "邮箱已保存，忘记密码时验证码会发到这个邮箱。");
        }
        return "redirect:/reader/profile";
    }

    /** 读者端退出（只清读者身份，管理端不受影响）。 */
    @GetMapping("/reader/logout")
    public String logout(HttpSession session, RedirectAttributes ra) {
        session.removeAttribute("reader_id");
        session.removeAttribute("reader_name");
        session.removeAttribute("reader_card");
        Flash.ok(ra, "已退出读者中心");
        return "redirect:/login";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
