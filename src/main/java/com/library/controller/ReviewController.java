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
import com.library.dao.ReviewDao;
import com.library.entity.Book;
import com.library.entity.BookReview;
import com.library.service.LogService;
import com.library.service.LoginContext;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;

import static com.library.controller.BookController.param;

/** 书评：读者给借过的书打分评价；管理端审核（隐藏/恢复/删除）。 */
@Controller
public class ReviewController {

    /** 评分转星星（模板里显示用）。 */
    public static String stars(int rating) {
        return "★".repeat(Math.max(1, Math.min(5, rating)));
    }

    private final ReviewDao reviewDao;
    private final BookDao bookDao;
    private final LogService logService;
    private final LoginContext loginContext;

    public ReviewController(ReviewDao reviewDao, BookDao bookDao, LogService logService,
                            LoginContext loginContext) {
        this.reviewDao = reviewDao;
        this.bookDao = bookDao;
        this.logService = logService;
        this.loginContext = loginContext;
    }

    // ---------------- 读者端 ----------------

    /** 读者写/改评价（必须借过这本书）。 */
    @GetMapping("/reader/review/{bookId}")
    public String reviewForm(@PathVariable Integer bookId, Model model, RedirectAttributes ra) {
        var reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        Book book = bookDao.findById(bookId);
        if (book == null) {
            return "redirect:/reader/my";
        }
        if (!reviewDao.hasBorrowed(reader.getId(), bookId)) {
            Flash.error(ra, "只有借过这本书的读者才能评价，谢谢理解。");
            return "redirect:/reader/my";
        }
        BookReview mine = reviewDao.findMine(bookId, reader.getId());
        model.addAttribute("book", book);
        model.addAttribute("mine", mine);
        model.addAttribute("reader", reader);
        model.addAttribute("active", "my");
        return "reader_review";
    }

    @PostMapping("/reader/review/{bookId}")
    public String saveReview(@PathVariable Integer bookId, HttpServletRequest request,
                             RedirectAttributes ra) {
        var reader = loginContext.currentReader();
        if (reader == null) {
            return "redirect:/login";
        }
        Book book = bookDao.findById(bookId);
        if (book == null) {
            return "redirect:/reader/my";
        }
        if (!reviewDao.hasBorrowed(reader.getId(), bookId)) {
            Flash.error(ra, "只有借过这本书的读者才能评价，谢谢理解。");
            return "redirect:/reader/my";
        }
        int rating = 5;
        try {
            rating = Integer.parseInt(request.getParameter("rating"));
        } catch (NumberFormatException | NullPointerException e) {
            rating = 5;
        }
        rating = Math.max(1, Math.min(5, rating));
        String content = param(request, "content");
        if (content.length() > 500) {
            content = content.substring(0, 500);
        }
        BookReview mine = reviewDao.findMine(bookId, reader.getId());
        reviewDao.upsert(bookId, reader.getId(), rating, content, mine != null,
                mine == null ? null : mine.getId());
        logService.action("发表书评", "《" + book.getTitle() + "》", rating + " 星");
        Flash.ok(ra, "评价已保存，谢谢你的分享！");
        return "redirect:/reader/my";
    }

    // ---------------- 管理端 ----------------

    @GetMapping("/reviews")
    public String list(@RequestParam(required = false) String q, Model model) {
        List<BookReview> rows = reviewDao.list(q);
        model.addAttribute("rows", rows);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("active", "reviews");
        return "reviews";
    }

    @PostMapping("/reviews/{reviewId}/{action}")
    public String action(@PathVariable Integer reviewId, @PathVariable String action,
                         HttpServletRequest request, RedirectAttributes ra) {
        BookReview row = reviewDao.findById(reviewId);
        if (row == null) {
            return "redirect:/reviews";
        }
        switch (action) {
            case "hide" -> {
                reviewDao.setHidden(reviewId, 1);
                Flash.ok(ra, "已隐藏对《" + row.getBookTitle() + "》的这条评价。");
                logService.action("隐藏书评", "《" + row.getBookTitle() + "》");
            }
            case "show" -> {
                reviewDao.setHidden(reviewId, 0);
                Flash.ok(ra, "已恢复显示。");
            }
            case "delete" -> {
                reviewDao.delete(reviewId);
                Flash.ok(ra, "评价已删除。");
                logService.action("删除书评", "《" + row.getBookTitle() + "》");
            }
            default -> {
                return "redirect:/reviews";
            }
        }
        String backQ = request.getParameter("back_q");
        return "redirect:/reviews" + (backQ != null && !backQ.isBlank() ? "?q=" + backQ : "");
    }
}
