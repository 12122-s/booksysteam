package com.library.controller;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.dao.AuxDao;
import com.library.dao.ReaderDao;
import com.library.entity.PasswordReset;
import com.library.entity.Reader;
import com.library.service.LoginContext;
import com.library.service.MailService;
import com.library.service.SettingsService;
import com.library.util.DateUtil;
import com.library.util.PasswordUtil;
import com.library.web.Flash;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import static com.library.controller.BookController.param;
import static com.library.controller.BookController.singleParams;

/**
 * 读者认证流程：设置网上密码（已有卡号注册）/ 自助办卡 / 找回密码（验证码）。
 * 验证码 30 分钟有效、错 5 次作废、60 秒内不能重发。
 */
@Controller
public class ReaderAuthController {

    private static final Logger log = LoggerFactory.getLogger(ReaderAuthController.class);

    private static final int RESET_CODE_MINUTES = 30;
    private static final int RESET_RESEND_SECONDS = 60;
    private static final int RESET_MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReaderDao readerDao;
    private final AuxDao auxDao;
    private final SettingsService settings;
    private final MailService mailService;
    private final LoginContext loginContext;

    public ReaderAuthController(ReaderDao readerDao, AuxDao auxDao, SettingsService settings,
                                MailService mailService, LoginContext loginContext) {
        this.readerDao = readerDao;
        this.auxDao = auxDao;
        this.settings = settings;
        this.mailService = mailService;
        this.loginContext = loginContext;
    }

    /** /reader/login 并入统一登录页，直接跳转。 */
    @GetMapping("/reader/login")
    public String readerLoginRedirect(@org.springframework.web.bind.annotation.RequestParam(
            name = "next", required = false) String next) {
        return "redirect:/login" + (next != null && !next.isBlank()
                ? "?next=" + java.net.URLEncoder.encode(next, java.nio.charset.StandardCharsets.UTF_8)
                : "");
    }

    // ---------------- 已有卡号，设置网上密码 ----------------

    @GetMapping("/reader/register")
    public String registerForm(Model model) {
        model.addAttribute("active", "register");
        return "reader_register";
    }

    @PostMapping("/reader/register")
    public String register(HttpServletRequest request, HttpSession session, RedirectAttributes ra,
                           Model model) {
        String cardNo = param(request, "card_no");
        String name = param(request, "name");
        String phone = param(request, "phone");
        String password = request.getParameter("password");
        String confirm = request.getParameter("confirm_password");

        Reader reader = readerDao.findByCardNo(cardNo);
        String error = null;
        if (cardNo.isEmpty() || name.isEmpty()) {
            error = "卡号和姓名都要填";
        } else if (reader == null) {
            error = "这个卡号在图书室没有登记过。请先到图书室办卡，或核对卡号是否输错。";
        } else if (reader.getPasswordHash() != null && !reader.getPasswordHash().isBlank()) {
            error = "这个卡号已经注册过了，请直接登录（忘记密码找管理员重置）。";
        } else if (!reader.getName().trim().equals(name)) {
            error = "姓名和图书室登记的档案不一致，请核对后重试。";
        } else if (!"正常".equals(reader.getStatus())) {
            error = "账号状态是「" + reader.getStatus() + "」，不能注册，请联系管理员。";
        } else if (notBlank(reader.getPhone()) && notBlank(phone)
                && !reader.getPhone().trim().equals(phone)) {
            error = "手机号对不上：图书室档案里登记的是 " + maskPhone(reader.getPhone()) + "。"
                    + "如果换号了，请找管理员更新号码；也可以直接让管理员帮你重置密码"
                    + "（重置后用 123456 登录再自己改）。";
        } else if (phone.isEmpty()) {
            error = "请填写手机号，方便图书室联系你";
        } else if (password == null || password.length() < 6) {
            error = "密码至少要 6 位";
        } else if (!password.equals(confirm)) {
            error = "两次输入的密码不一样，请重新输入";
        }
        if (error != null) {
            Flash.error(ra, error);
            return "redirect:/reader/register";
        }
        readerDao.updatePassword(reader.getId(), PasswordUtil.hash(password));
        readerDao.updatePhone(reader.getId(), phone);

        session.setAttribute("reader_id", reader.getId());
        session.setAttribute("reader_name", reader.getName());
        session.setAttribute("reader_card", reader.getCardNo());
        log.info("读者设置网上密码成功：{}（卡号 {}）", reader.getName(), reader.getCardNo());
        Flash.ok(ra, "注册成功，欢迎你，" + reader.getName() + "！现在可以上网借书了。");
        return "redirect:/reader/";
    }

    // ---------------- 自助办卡（没有卡的新读者） ----------------

    @GetMapping("/reader/signup")
    public String signupForm(Model model) {
        model.addAttribute("active", "signup");
        return "reader_signup";
    }

    @PostMapping("/reader/signup")
    public String signup(@Valid @ModelAttribute com.library.dto.ReaderSignupForm form,
                         BindingResult bindingResult,
                         HttpServletRequest request,
                         HttpSession session,
                         RedirectAttributes ra) {
        if (!settings.getBool("reader_self_register")) {
            Flash.error(ra, "图书室暂时关闭了网上办卡，请到图书室现场办卡。");
            return "redirect:/login";
        }
        // 格式校验（姓名/手机号/密码长度），消息与页面文案一致
        if (bindingResult.hasErrors()) {
            Flash.error(ra, bindingResult.getFieldError().getDefaultMessage());
            return "redirect:/reader/signup";
        }
        String name = form.getName();
        String phone = form.getPhone();
        String password = form.getPassword();
        String confirm = request.getParameter("confirm_password");
        String readerType = param(request, "reader_type");
        if (readerType.isEmpty()) {
            readerType = "读者";
        }
        String department = param(request, "department");
        String email = param(request, "email");

        String error = null;
        if (readerDao.phoneExists(phone)) {
            error = "这个手机号已经办过卡了，请直接登录；忘记密码可以找管理员重置。";
        } else if (!password.equals(confirm)) {
            error = "两次输入的密码不一样，请重新输入";
        } else if (request.getParameter("agree") == null) {
            error = "请先阅读并同意借阅规则";
        }
        if (error != null) {
            Flash.error(ra, error);
            return "redirect:/reader/signup";
        }

        boolean needApproval = settings.getBool("reader_register_need_approval");
        String cardNo = readerDao.nextCardNo();
        Reader reader = new Reader();
        reader.setCardNo(cardNo);
        reader.setName(name);
        reader.setGender("");
        reader.setReaderType(readerType);
        reader.setDepartment(department);
        reader.setPhone(phone);
        reader.setEmail(email);
        reader.setPasswordHash(PasswordUtil.hash(password));
        reader.setStatus(needApproval ? "待审核" : "正常");
        reader.setSource("自助注册");
        int newId = readerDao.insertWithPassword(reader);
        log.info("读者自助办卡：{}（卡号 {}，{}）", name, cardNo,
                needApproval ? "待审核" : "直接生效");

        session.setAttribute("reader_id", newId);
        session.setAttribute("reader_name", name);
        session.setAttribute("reader_card", cardNo);
        if (needApproval) {
            Flash.warn(ra, "办卡成功！你的借书卡号是 " + cardNo + "。"
                    + "等图书室管理员确认后就能借书了（现在可以先查书）。");
        } else {
            Flash.ok(ra, "办卡成功！你的借书卡号是 " + cardNo + "，现在就可以借书了。"
                    + "（卡号请记好，用手机号也能登录）");
        }
        return "redirect:/reader/";
    }

    // ---------------- 找回密码 ----------------

    @GetMapping("/reader/forgot")
    public String forgotForm(Model model) {
        model.addAttribute("active", "login");
        return "reader_forgot";
    }

    @PostMapping("/reader/forgot")
    public String forgot(HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        String account = param(request, "account");
        Reader reader = readerDao.findByAccount(account);
        if (reader == null) {
            Flash.error(ra, "没有找到这个账号。请核对借书卡号或手机号，或到图书室找管理员。");
            return "redirect:/reader/forgot";
        }
        if ("停用".equals(reader.getStatus())) {
            Flash.error(ra, "账号已停用，请联系图书室管理员。");
            return "redirect:/reader/forgot";
        }
        if (reader.getPasswordHash() == null || reader.getPasswordHash().isBlank()) {
            Flash.error(ra, "这个账号还没设置过网上密码，请回登录页点「我已有卡号，设置网上密码」。");
            return "redirect:/reader/forgot";
        }

        // 60 秒内不能重发
        LocalDateTime now = LocalDateTime.now();
        PasswordReset last = auxDao.latestReset(reader.getId());
        if (last != null && last.getUsed() == 0 && notBlank(last.getCreatedAt())
                && last.getExpireAt() != null && last.getExpireAt().compareTo(DateUtil.nowStr()) > 0) {
            LocalDateTime createdAt = DateUtil.parseDateTime(last.getCreatedAt());
            if (createdAt != null && createdAt.isAfter(now.minusSeconds(RESET_RESEND_SECONDS))) {
                session.setAttribute("reset_reader_id", reader.getId());
                Flash.warn(ra, "验证码刚刚已经发过了，请稍等一分钟再试；也可以直接联系图书室管理员。");
                return "redirect:/reader/reset";
            }
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String expire = now.plusMinutes(RESET_CODE_MINUTES).format(DateUtil.DATETIME);
        // 同一读者只保留最新一个验证码，旧的作废
        auxDao.invalidateResets(reader.getId());
        auxDao.insertReset(reader.getId(), code, expire);
        session.setAttribute("reset_reader_id", reader.getId());

        MailService.SendResult sent = mailService.sendResetCode(reader.getEmail(), reader.getName(),
                code, RESET_CODE_MINUTES);
        if (sent.ok()) {
            Flash.ok(ra, "验证码已发送到你留的邮箱 " + maskEmail(reader.getEmail()) + "，"
                    + RESET_CODE_MINUTES + " 分钟内有效。");
        } else {
            // 没配邮箱或发送失败：由管理员人工转告验证码
            Flash.ok(ra, "验证码已生成。请拨打电话或到图书室找管理员，报出你的手机号 "
                    + maskPhone(reader.getPhone()) + "，管理员核对身份后会告诉你验证码"
                    + "（" + RESET_CODE_MINUTES + " 分钟内有效）。");
        }
        return "redirect:/reader/reset";
    }

    @GetMapping("/reader/reset")
    public String resetForm(HttpSession session, Model model, RedirectAttributes ra) {
        Integer readerId = (Integer) session.getAttribute("reset_reader_id");
        if (readerId == null) {
            Flash.warn(ra, "请先填写借书卡号或手机号，获取验证码。");
            return "redirect:/reader/forgot";
        }
        Reader reader = readerDao.findByIdActive(readerId);
        if (reader == null) {
            session.removeAttribute("reset_reader_id");
            return "redirect:/reader/forgot";
        }
        model.addAttribute("reader", reader);
        model.addAttribute("phone", maskPhone(reader.getPhone()));
        model.addAttribute("active", "login");
        return "reader_reset";
    }

    @PostMapping("/reader/reset")
    public String reset(HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        Integer readerId = (Integer) session.getAttribute("reset_reader_id");
        if (readerId == null) {
            Flash.warn(ra, "请先填写借书卡号或手机号，获取验证码。");
            return "redirect:/reader/forgot";
        }
        Reader reader = readerDao.findByIdActive(readerId);
        if (reader == null) {
            session.removeAttribute("reset_reader_id");
            return "redirect:/reader/forgot";
        }
        String code = param(request, "code");
        String password = request.getParameter("password");
        String confirm = request.getParameter("confirm_password");

        PasswordReset record = auxDao.latestUnusedReset(readerId);
        if (record == null) {
            Flash.error(ra, "还没有获取验证码，请返回上一步重新获取。");
        } else if (record.getExpireAt().compareTo(DateUtil.nowStr()) < 0) {
            Flash.error(ra, "验证码已经过期了，请重新获取一个新的。");
        } else if (record.getAttempts() >= RESET_MAX_ATTEMPTS) {
            Flash.error(ra, "验证码输错次数太多了，请重新获取一个新的验证码。");
        } else if (!record.getCode().equals(code)) {
            auxDao.incrementResetAttempts(record.getId());
            int left = Math.max(0, RESET_MAX_ATTEMPTS - record.getAttempts() - 1);
            Flash.error(ra, "验证码不正确，还可以再试 " + left + " 次。");
        } else if (password == null || password.length() < 6) {
            Flash.error(ra, "新密码至少要 6 位。");
        } else if (!password.equals(confirm)) {
            Flash.error(ra, "两次输入的新密码不一样，请重新输入。");
        } else {
            readerDao.updatePassword(readerId, PasswordUtil.hash(password));
            auxDao.markResetUsed(readerId);
            session.removeAttribute("reset_reader_id");
            log.info("读者重置密码成功：{}（卡号 {}）", reader.getName(), reader.getCardNo());
            Flash.ok(ra, "密码修改成功，请用新密码登录。");
            return "redirect:/login";
        }
        return "redirect:/reader/reset";
    }

    // ---- 遮掩工具 ----

    private static String maskPhone(String phone) {
        String p = phone == null ? "" : phone.trim();
        return p.length() >= 8 ? p.substring(0, 3) + "****" + p.substring(p.length() - 4)
                : (p.isEmpty() ? "—" : p);
    }

    private static String maskEmail(String email) {
        String e = email == null ? "" : email.trim();
        if (!e.contains("@")) {
            return e.isEmpty() ? "—" : e;
        }
        String name = e.split("@", 2)[0];
        String domain = e.split("@", 2)[1];
        return (name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2)) + "***@" + domain;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
