package com.library.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.dao.BookDao;
import com.library.dao.BorrowDao;
import com.library.entity.Book;
import com.library.entity.BorrowRecord;
import com.library.service.BorrowService;
import com.library.service.LogService;
import com.library.service.SettingsService;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;

/** 图书管理：列表 / 新增 / 编辑 / 删除（下架）/ 借阅历史。 */
@Controller
@RequestMapping("/books")
public class BookController {

    private final BookDao bookDao;
    private final BorrowDao borrowDao;
    private final BorrowService borrowService;
    private final LogService logService;
    private final SettingsService settings;

    public BookController(BookDao bookDao, BorrowDao borrowDao, BorrowService borrowService,
                          LogService logService, SettingsService settings) {
        this.bookDao = bookDao;
        this.borrowDao = borrowDao;
        this.borrowService = borrowService;
        this.logService = logService;
        this.settings = settings;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String available,
                       @RequestParam(defaultValue = "1") Integer page,
                       Model model) {
        boolean onlyAvailable = "1".equals(available);
        BookDao.ListResult<Book> result =
                bookDao.list(q, category, onlyAvailable, page, 15);
        model.addAttribute("books", result.items());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("onlyAvailable", onlyAvailable);
        model.addAttribute("page", result.page());
        model.addAttribute("pages", result.pages());
        model.addAttribute("total", result.total());
        model.addAttribute("categories", bookDao.categories());
        model.addAttribute("active", "books");
        return "books";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("book", null);
        model.addAttribute("active", "books");
        return "book_form";
    }

    @PostMapping("/new")
    public String create(jakarta.servlet.http.HttpServletRequest request, RedirectAttributes ra, Model model) {
        String error = save(null, request);
        if (error != null) {
            Flash.error(ra, error);
            ra.addFlashAttribute("form", singleParams(request));
            return "redirect:/books/new";
        }
        Flash.ok(ra, "图书已保存，可以在图书列表里搜到它");
        logService.action("新增图书", param(request, "title"), "册数 " + paramOr(request, "total_copies", "1"));
        return "redirect:/books";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        Book book = bookDao.findById(id);
        if (book == null) {
            return "redirect:/books";
        }
        model.addAttribute("book", book);
        model.addAttribute("active", "books");
        return "book_form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Integer id, HttpServletRequest request,
                         RedirectAttributes ra, Model model) {
        Book book = bookDao.findById(id);
        if (book == null) {
            return "redirect:/books";
        }
        String error = save(id, request);
        if (error != null) {
            Flash.error(ra, error);
            ra.addFlashAttribute("form", singleParams(request));
            return "redirect:/books/" + id + "/edit";
        }
        Flash.ok(ra, "图书信息已更新");
        logService.action("修改图书", param(request, "title"));
        return "redirect:/books";
    }

    /** 表单提交值的单值快照（保存失败回显用）。 */
    static java.util.Map<String, String> singleParams(HttpServletRequest request) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> map.put(k, v == null || v.length == 0 ? "" : v[0]));
        return map;
    }

    /** 新增/更新的公共校验与落库；返回错误提示，null 表示成功。 */
    private String save(Integer bookId, HttpServletRequest request) {
        String title = param(request, "title");
        if (title.isEmpty()) {
            return "书名不能为空";
        }
        int total;
        try {
            total = Integer.parseInt(paramOr(request, "total_copies", "1"));
        } catch (NumberFormatException e) {
            return "总册数请填写数字";
        }
        if (total < 1) {
            return "总册数至少为 1 册";
        }
        Integer year = null;
        String yearRaw = param(request, "publish_year");
        if (!yearRaw.isEmpty()) {
            try {
                year = Integer.parseInt(yearRaw);
            } catch (NumberFormatException e) {
                return "出版年份请填写数字，例如 2020";
            }
        }
        Double price = null;
        String priceRaw = param(request, "price");
        if (!priceRaw.isEmpty()) {
            try {
                price = Double.parseDouble(priceRaw);
            } catch (NumberFormatException e) {
                return "单价请填写数字，例如 39.5";
            }
        }

        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(param(request, "author"));
        book.setCategory(param(request, "category"));
        book.setIsbn(param(request, "isbn"));
        book.setPublisher(param(request, "publisher"));
        book.setPublishYear(year);
        book.setPrice(price);
        book.setLocation(param(request, "location"));
        book.setSummary(param(request, "summary"));

        if (bookId == null) {
            book.setTotalCopies(total);
            book.setAvailableCopies(total);
            bookDao.insert(book, com.library.util.DateUtil.nowStr());
            return null;
        }
        Book old = bookDao.findById(bookId);
        if (old == null) {
            return "这本书不存在，可能已被删除";
        }
        int borrowedOut = old.getTotalCopies() - old.getAvailableCopies();
        if (total < borrowedOut) {
            return "当前有 " + borrowedOut + " 册还没还回来，总册数不能少于 " + borrowedOut + " 册";
        }
        book.setId(bookId);
        book.setTotalCopies(total);
        book.setAvailableCopies(total - borrowedOut);
        bookDao.update(book);
        return null;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, RedirectAttributes ra) {
        Book book = bookDao.findById(id);
        if (book == null) {
            return "redirect:/books";
        }
        List<BorrowDao.ActiveBorrow> active = borrowDao.activeBorrowsForBook(id);
        if (!active.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (BorrowDao.ActiveBorrow a : active) {
                if (names.length() > 0) {
                    names.append("、");
                }
                names.append(a.readerName()).append("（应还 ").append(a.dueDate()).append("）");
            }
            Flash.error(ra, "《" + book.getTitle() + "》还有 " + active.size() + " 册没收回来："
                    + names + "。不能删除，请先办理还书。");
            return "redirect:/books";
        }
        // 有借阅历史的书不真删，只做“下架”，保证历史记录不会断掉
        String message;
        if (bookDao.borrowHistoryCount(id) > 0) {
            bookDao.markDeleted(id);
            message = "《" + book.getTitle() + "》已下架，历史借阅记录仍然保留";
        } else {
            bookDao.delete(id);
            message = "《" + book.getTitle() + "》已删除";
        }
        Flash.ok(ra, message);
        logService.action("删除图书", book.getTitle());
        return "redirect:/books";
    }

    @GetMapping("/{id}/history")
    public String history(@PathVariable Integer id, Model model) {
        Book book = bookDao.findById(id);
        if (book == null) {
            return "redirect:/books";
        }
        List<BorrowRecord> records = borrowDao.allByBook(id);
        borrowService.decorate(records);
        model.addAttribute("book", book);
        model.addAttribute("records", records);
        model.addAttribute("active", "books");
        return "book_history";
    }

    // ---- 表单参数小工具 ----

    static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    static String paramOr(HttpServletRequest request, String name, String fallback) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
