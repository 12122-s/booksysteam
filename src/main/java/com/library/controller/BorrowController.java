package com.library.controller;

import java.time.LocalDate;
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
import com.library.dao.ReaderDao;
import com.library.entity.Book;
import com.library.entity.BorrowRecord;
import com.library.entity.Reader;
import com.library.service.BorrowService;
import com.library.service.LogService;
import com.library.service.MailService;
import com.library.service.SettingsService;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;

import static com.library.controller.BookController.param;

/** 借阅管理：记录列表 / 办理借书 / 还书 / 续借。 */
@Controller
public class BorrowController {

    private final BorrowDao borrowDao;
    private final BookDao bookDao;
    private final ReaderDao readerDao;
    private final BorrowRequestDao requestDao;
    private final BorrowService borrowService;
    private final SettingsService settings;
    private final LogService logService;
    private final MailService mailService;

    public BorrowController(BorrowDao borrowDao, BookDao bookDao, ReaderDao readerDao,
                            BorrowRequestDao requestDao, BorrowService borrowService,
                            SettingsService settings, LogService logService,
                            MailService mailService) {
        this.borrowDao = borrowDao;
        this.bookDao = bookDao;
        this.readerDao = readerDao;
        this.requestDao = requestDao;
        this.borrowService = borrowService;
        this.settings = settings;
        this.logService = logService;
        this.mailService = mailService;
    }

    @GetMapping("/borrows")
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") Integer page,
                       Model model) {
        BookDao.ListResult<BorrowRecord> result =
                borrowDao.list(status, q, page, 15, LocalDate.now().toString());
        borrowService.decorate(result.items());

        int[] counts = borrowDao.counts(LocalDate.now().toString());
        model.addAttribute("records", result.items());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("page", result.page());
        model.addAttribute("pages", result.pages());
        model.addAttribute("total", result.total());
        model.addAttribute("countAll", counts[0]);
        model.addAttribute("countBorrowing", counts[1]);
        model.addAttribute("countOverdue", counts[2]);
        model.addAttribute("countReturned", counts[3]);
        model.addAttribute("active", "borrows");
        return "borrows";
    }

    @GetMapping("/borrow/new")
    public String newBorrow(@RequestParam(required = false) String rq,
                            @RequestParam(required = false) String bq,
                            @RequestParam(name = "pick_reader", required = false) Integer pickReader,
                            @RequestParam(name = "pick_book", required = false) Integer pickBook,
                            @RequestParam(name = "reader_id", required = false) Integer readerIdParam,
                            @RequestParam(name = "book_id", required = false) Integer bookIdParam,
                            Model model) {
        int borrowDays = settings.getInt("borrow_days");
        int maxBooks = settings.getInt("max_books_per_reader");

        Integer readerId = pickReader != null ? pickReader : readerIdParam;
        Integer bookId = pickBook != null ? pickBook : bookIdParam;
        String readerQuery = rq == null ? "" : rq.trim();
        String bookQuery = bq == null ? "" : bq.trim();

        // 读者候选：按姓名/卡号/部门搜，最多 20 条
        List<Reader> readers;
        if (readerQuery.isEmpty()) {
            readers = readerDao.listAll(
                    "SELECT r.*, (SELECT COUNT(*) FROM borrow_records br WHERE br.reader_id = r.id "
                            + "AND br.return_date IS NULL) AS borrowing FROM readers r "
                            + "WHERE r.is_deleted = 0 ORDER BY r.name LIMIT 20");
        } else {
            String like = "%" + readerQuery + "%";
            readers = readerDao.listAll(
                    "SELECT r.*, (SELECT COUNT(*) FROM borrow_records br WHERE br.reader_id = r.id "
                            + "AND br.return_date IS NULL) AS borrowing FROM readers r "
                            + "WHERE r.is_deleted = 0 AND (r.name LIKE ? OR r.card_no LIKE ? OR r.department LIKE ?) "
                            + "ORDER BY r.name LIMIT 20", like, like, like);
        }
        // 图书候选：按书名/作者/ISBN 搜，最多 20 条
        List<Book> books;
        if (bookQuery.isEmpty()) {
            books = bookDao.listAll("SELECT * FROM books WHERE is_deleted = 0 ORDER BY title LIMIT 20");
        } else {
            String like = "%" + bookQuery + "%";
            books = bookDao.listAll(
                    "SELECT * FROM books WHERE is_deleted = 0 AND (title LIKE ? OR author LIKE ? "
                            + "OR isbn LIKE ?) ORDER BY title LIMIT 20", like, like, like);
        }

        Reader selectedReader = readerId == null ? null : readerDao.findById(readerId);
        Book selectedBook = bookId == null ? null : bookDao.findById(bookId);
        int readerOverdue = 0;
        if (selectedReader != null) {
            readerOverdue = readerDao.overdueCount(selectedReader.getId(), LocalDate.now().toString());
        }

        model.addAttribute("readers", readers);
        model.addAttribute("books", books);
        model.addAttribute("rq", readerQuery);
        model.addAttribute("bq", bookQuery);
        model.addAttribute("selectedReader", selectedReader);
        model.addAttribute("selectedBook", selectedBook);
        model.addAttribute("readerOverdue", readerOverdue);
        model.addAttribute("borrowDays", borrowDays);
        model.addAttribute("maxBooks", maxBooks);
        model.addAttribute("defaultDue", LocalDate.now().plusDays(borrowDays).toString());
        model.addAttribute("active", "borrow_new");
        return "borrow_new";
    }

    @PostMapping("/borrow/new")
    public String createBorrow(HttpServletRequest request, RedirectAttributes ra,
                               @RequestParam(name = "reader_id", required = false) Integer readerId,
                               @RequestParam(name = "book_id", required = false) Integer bookId) {
        if (readerId == null || bookId == null) {
            Flash.error(ra, "请先选择读者和图书，再点确认借出");
            return "redirect:/borrow/new";
        }
        BorrowService.OpResult result =
                borrowService.borrowNew(bookId, readerId, param(request, "due_date"), param(request, "remark"));
        if (!result.ok()) {
            Flash.error(ra, result.message());
            StringBuilder target = new StringBuilder("/borrow/new");
            if (readerId != null) {
                target.append("?reader_id=").append(readerId);
                if (bookId != null) {
                    target.append("&book_id=").append(bookId);
                }
            }
            return "redirect:" + target;
        }
        Flash.ok(ra, result.message());
        logService.action("办理借书", result.message().replace("借出成功：", ""));
        return "redirect:/borrows";
    }

    @PostMapping("/borrows/{id}/return")
    public String returnBook(@PathVariable Integer id, HttpServletRequest request, RedirectAttributes ra) {
        BorrowRecord record = borrowDao.findById(id);
        if (record == null) {
            return "redirect:/borrows";
        }
        if (record.getReturnDate() != null) {
            Flash.error(ra, "这本书已经办理过还书了");
            String next = request.getParameter("next");
            return "redirect:" + (next != null && next.startsWith("/") ? next : "/borrows");
        }
        BorrowService.ReturnOutcome outcome = borrowService.returnBook(id, request.getParameter("fine_amount"));
        if (outcome.overdueDays() > 0) {
            Flash.warn(ra, "还书成功：《" + outcome.record().getBookTitle() + "》已逾期 "
                    + outcome.overdueDays() + " 天，应收罚金 "
                    + String.format("%.2f", outcome.fine()) + " 元");
        } else {
            Flash.ok(ra, "还书成功：《" + outcome.record().getBookTitle() + "》已归还");
        }

        // 还书后：把排队第一位的申请置为“可借”，并提示管理员
        var first = outcome.first();
        if (first != null) {
            boolean notifyMail = settings.getBool("mail_notify_ready");
            String extra;
            if (!notifyMail) {
                extra = "（邮件通知已关闭，请电话或当面通知他）";
            } else {
                var mailResult = mailNotifyReady(first);
                extra = mailResult ? "（已发邮件通知他）" : "（邮件没发出去，请电话通知他）";
            }
            Flash.warn(ra, "《" + first.getBookTitle() + "》到馆了，已通知预约的 "
                    + first.getReaderName() + "（" + (first.getPhone() == null || first.getPhone().isBlank()
                    ? "没留电话" : first.getPhone()) + "）来取" + extra + "。");
        }
        if (!outcome.waiting().isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (var w : outcome.waiting()) {
                if (names.length() > 0) {
                    names.append("、");
                }
                names.append(w.readerName()).append("（")
                        .append(w.phone() == null || w.phone().isBlank() ? "没留电话" : w.phone()).append("）");
            }
            Flash.warn(ra, "这本书还有 " + outcome.waiting().size() + " 位读者在等：" + names
                    + "。请到「借阅申请」里办理。");
        }
        logService.action("办理还书",
                "《" + outcome.record().getBookTitle() + "》— " + outcome.record().getReaderName(),
                outcome.overdueDays() > 0
                        ? "逾期 " + outcome.overdueDays() + " 天，罚金 " + String.format("%.2f", outcome.fine()) + " 元"
                        : "按时归还");
        String next = request.getParameter("next");
        return "redirect:" + (next != null && next.startsWith("/") ? next : "/borrows");
    }

    private boolean mailNotifyReady(com.library.entity.BorrowRequest first) {
        String body = first.getReaderName() + "，你好：\n\n"
                + "你预约的《" + first.getBookTitle() + "》已经还回来了，请到图书室取书。\n"
                + "到馆后找管理员办理借出即可。\n";
        return mailService.send(first.getEmail(),
                "【到馆通知】你预约的《" + first.getBookTitle() + "》已到馆", body).ok();
    }

    @PostMapping("/borrows/{id}/renew")
    public String renew(@PathVariable Integer id, HttpServletRequest request, RedirectAttributes ra) {
        Integer days = parseIntOrNull(request.getParameter("days"));
        BorrowService.OpResult result = borrowService.renew(id, days, 0);
        if (result == null) {
            return "redirect:/borrows";
        }
        if (result.ok()) {
            Flash.ok(ra, result.message());
            logService.action("续借", result.message());
        } else {
            Flash.error(ra, result.message());
        }
        String next = request.getParameter("next");
        return "redirect:" + (next != null && next.startsWith("/") ? next : "/borrows");
    }

    private Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
