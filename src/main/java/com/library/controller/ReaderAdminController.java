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
import com.library.dao.ReaderDao;
import com.library.entity.BorrowRecord;
import com.library.entity.Reader;
import com.library.service.BorrowService;
import com.library.service.LogService;
import com.library.util.DateUtil;
import com.library.util.PasswordUtil;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;

import static com.library.controller.BookController.param;
import static com.library.controller.BookController.singleParams;

/** 读者管理：列表 / 新增 / 编辑 / 删除（注销）/ 历史 / 重置密码 / 办卡审核。 */
@Controller
@RequestMapping("/readers")
public class ReaderAdminController {

    private final ReaderDao readerDao;
    private final BorrowDao borrowDao;
    private final BorrowService borrowService;
    private final LogService logService;

    public ReaderAdminController(ReaderDao readerDao, BorrowDao borrowDao,
                                 BorrowService borrowService, LogService logService) {
        this.readerDao = readerDao;
        this.borrowDao = borrowDao;
        this.borrowService = borrowService;
        this.logService = logService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "1") Integer page,
                       Model model) {
        BookDao.ListResult<Reader> result =
                readerDao.list(q, type, page, 15, DateUtil.todayStr());
        model.addAttribute("readers", result.items());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("rtype", type == null ? "" : type);
        model.addAttribute("page", result.page());
        model.addAttribute("pages", result.pages());
        model.addAttribute("total", result.total());
        model.addAttribute("types", readerDao.types());
        model.addAttribute("active", "readers");
        return "readers";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("reader", null);
        model.addAttribute("active", "readers");
        return "reader_form";
    }

    @PostMapping("/new")
    public String create(HttpServletRequest request, RedirectAttributes ra) {
        String error = save(null, request);
        if (error != null) {
            Flash.error(ra, error);
            ra.addFlashAttribute("form", singleParams(request));
            return "redirect:/readers/new";
        }
        Flash.ok(ra, "读者已保存");
        logService.action("新增读者", param(request, "name"), "卡号 " + param(request, "card_no"));
        return "redirect:/readers";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        Reader reader = readerDao.findById(id);
        if (reader == null) {
            return "redirect:/readers";
        }
        model.addAttribute("reader", reader);
        model.addAttribute("active", "readers");
        return "reader_form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Integer id, HttpServletRequest request, RedirectAttributes ra) {
        if (readerDao.findById(id) == null) {
            return "redirect:/readers";
        }
        String error = save(id, request);
        if (error != null) {
            Flash.error(ra, error);
            ra.addFlashAttribute("form", singleParams(request));
            return "redirect:/readers/" + id + "/edit";
        }
        Flash.ok(ra, "读者信息已更新");
        logService.action("修改读者", param(request, "name"));
        return "redirect:/readers";
    }

    /** 新增/更新读者；返回错误提示，null 表示成功。 */
    private String save(Integer readerId, HttpServletRequest request) {
        String cardNo = param(request, "card_no");
        String name = param(request, "name");
        if (cardNo.isEmpty()) {
            return "借书卡号不能为空";
        }
        if (name.isEmpty()) {
            return "姓名不能为空";
        }
        String phone = param(request, "phone");
        if (!phone.isEmpty() && !phone.replace("-", "").replace(" ", "").matches("\\d+")) {
            return "联系电话请填写数字";
        }

        Reader existing = readerDao.findByCardNo(cardNo);
        Integer targetId = readerId;
        if (existing != null && readerId != null && existing.getId().equals(readerId)) {
            existing = null;   // 改的是同一个人，不算重复
        }
        if (existing != null) {
            if (existing.getIsDeleted() == 0) {
                return "卡号 " + cardNo + " 已经有读者在用了，请换一个卡号";
            }
            // 卡号属于已注销的读者：复用原来那条记录，历史借阅记录继续保留
            targetId = existing.getId();
        }

        Reader reader = new Reader();
        reader.setCardNo(cardNo);
        reader.setName(name);
        reader.setGender(param(request, "gender"));
        reader.setReaderType(param(request, "reader_type"));
        reader.setDepartment(param(request, "department"));
        reader.setPhone(phone);
        reader.setEmail(param(request, "email"));
        String status = param(request, "status");
        reader.setStatus(status.isEmpty() ? "正常" : status);

        if (targetId == null) {
            readerDao.insert(reader);
        } else {
            reader.setId(targetId);
            readerDao.update(reader);
        }
        return null;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Integer id, RedirectAttributes ra) {
        Reader reader = readerDao.findById(id);
        if (reader == null) {
            return "redirect:/readers";
        }
        List<BorrowDao.ActiveBorrow> active = borrowDao.activeBorrowsForReader(id);
        if (!active.isEmpty()) {
            StringBuilder books = new StringBuilder();
            for (BorrowDao.ActiveBorrow a : active) {
                if (books.length() > 0) {
                    books.append("、");
                }
                books.append("《").append(a.bookTitle()).append("》（应还 ").append(a.dueDate()).append("）");
            }
            Flash.error(ra, reader.getName() + " 还有 " + active.size() + " 册书没还：" + books
                    + "。不能删除，请先办理还书。");
            return "redirect:/readers";
        }
        // 有借阅历史的读者不真删，只做“注销”，历史记录继续保留
        String message;
        if (readerDao.borrowHistoryCount(id) > 0) {
            readerDao.markDeleted(id);
            message = "读者 " + reader.getName() + " 已注销，历史借阅记录仍然保留";
        } else {
            readerDao.delete(id);
            message = "读者 " + reader.getName() + " 已删除";
        }
        Flash.ok(ra, message);
        logService.action("删除/注销读者", reader.getName(), "卡号 " + reader.getCardNo());
        return "redirect:/readers";
    }

    @GetMapping("/{id}/history")
    public String history(@PathVariable Integer id, Model model) {
        Reader reader = readerDao.findById(id);
        if (reader == null) {
            return "redirect:/readers";
        }
        List<BorrowRecord> records = borrowDao.allByReader(id);
        borrowService.decorate(records);
        double unpaid = records.stream()
                .filter(r -> "已逾期".equals(r.getStatus()))
                .mapToDouble(BorrowRecord::getFine).sum();
        model.addAttribute("reader", reader);
        model.addAttribute("records", records);
        model.addAttribute("unpaid", Math.round(unpaid * 100.0) / 100.0);
        model.addAttribute("active", "readers");
        return "reader_history";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Integer id, RedirectAttributes ra) {
        Reader reader = readerDao.findByIdActive(id);
        if (reader == null) {
            return "redirect:/readers";
        }
        // 重置成初始密码 123456，请读者登录后自行修改
        readerDao.updatePassword(id, PasswordUtil.hash("123456"));
        Flash.ok(ra, "已把 " + reader.getName() + " 的网上账号密码重置为 123456，"
                + "请转告他/她登录后自行修改。");
        logService.action("重置读者密码", reader.getName(), "卡号 " + reader.getCardNo());
        return "redirect:/readers";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Integer id, RedirectAttributes ra) {
        Reader reader = readerDao.findByIdActive(id);
        if (reader == null) {
            return "redirect:/readers";
        }
        readerDao.setStatus(id, "正常");
        Flash.ok(ra, "已确认 " + reader.getName() + " 的办卡申请，他/她现在可以借书了。");
        logService.action("通过办卡审核", reader.getName(), "卡号 " + reader.getCardNo());
        return "redirect:/readers";
    }
}
