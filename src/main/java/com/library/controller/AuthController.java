package com.library.controller;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.library.dao.AuxDao;
import com.library.dao.ReaderDao;
import com.library.entity.Reader;
import com.library.service.SettingsService;
import com.library.util.PasswordUtil;
import com.library.web.BootToken;
import com.library.web.Flash;
import com.library.web.GlobalModelAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 统一登录入口：管理员进管理端，读者（卡号/手机号）进读者端；登出；健康检查。
 * 与 Python 版 login / logout / logout_all / health / api_pending_resets 一一对应。
 */
@Controller
public class AuthController {

    private final SettingsService settings;
    private final ReaderDao readerDao;
    private final AuxDao auxDao;
    private final BootToken bootToken;

    public AuthController(SettingsService settings, ReaderDao readerDao, AuxDao auxDao,
                          BootToken bootToken) {
        this.settings = settings;
        this.readerDao = readerDao;
        this.auxDao = auxDao;
        this.bootToken = bootToken;
    }

    /** 是否还在用初始密码 123456（登录页上提醒）。 */
    private boolean isDefaultPassword() {
        String hashed = settings.getAdminPasswordHash();
        return hashed.isEmpty() || PasswordUtil.check(hashed, "123456");
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("defaultPassword", isDefaultPassword());
        String adminAccount = settings.get("admin_account");
        if (adminAccount == null || adminAccount.isBlank()) {
            adminAccount = "admin";
        }
        model.addAttribute("adminAccount", adminAccount.trim());
        model.addAttribute("next", next == null ? "" : next);
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String account,
                        @RequestParam String password,
                        @RequestParam(name = "remember", required = false) String remember,
                        @RequestParam(required = false) String next,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        RedirectAttributes ra) {
        String adminAccount = settings.get("admin_account");
        if (adminAccount == null || adminAccount.isBlank()) {
            adminAccount = "admin";
        }
        String input = account == null ? "" : account.trim();

        // ① 管理员：账号名对得上，就只校验管理密码
        if (input.equalsIgnoreCase(adminAccount.trim())) {
            String hashed = settings.getAdminPasswordHash();
            if (!hashed.isEmpty() && PasswordUtil.check(hashed, password)) {
                if (!settings.getBool("allow_multi_login")) {
                    // 安全模式（默认）：管理端登录时清掉同浏览器里的读者身份。
                    // 注意不能 invalidate 会话：Tomcat 在同一请求里销毁再建会话
                    // 不会重发 JSESSIONID Cookie，会导致登录状态丢失。
                    HttpSession old = request.getSession(false);
                    if (old != null) {
                        old.removeAttribute("reader_id");
                        old.removeAttribute("reader_name");
                        old.removeAttribute("reader_card");
                    }
                }
                HttpSession session = request.getSession(true);
                session.setAttribute("logged_in", Boolean.TRUE);
                applySessionPolicy(session, remember, response);
                Flash.ok(ra, "登录成功，欢迎回来");
                return "redirect:" + safeAdminTarget(next);
            }
            Flash.error(ra, "管理员密码不正确，请重新输入。");
            return "redirect:/login";
        }

        // ② 读者：账号 = 借书卡号 或 手机号
        if (input.isEmpty()) {
            Flash.error(ra, "请填写账号：管理员填管理员账号，读者填借书卡号或手机号。");
            return "redirect:/login";
        }
        Reader reader = readerDao.findByAccount(input);
        if (reader == null) {
            Flash.error(ra, "没有找到这个账号。管理员请用管理员账号登录；"
                    + "读者请用借书卡号或手机号登录，新读者请点下面「我要办卡」。");
        } else if (reader.getPasswordHash() == null || reader.getPasswordHash().isBlank()) {
            Flash.error(ra, "这个卡号还没有设置网上密码，请点下面「我已有卡号，设置密码」完成注册。");
        } else if ("停用".equals(reader.getStatus())) {
            Flash.error(ra, "账号已停用，暂时不能借书，请联系图书室管理员。");
        } else if (!PasswordUtil.check(reader.getPasswordHash(), password)) {
            Flash.error(ra, "密码不正确，请重新输入。");
        } else {
            boolean multiLogin = settings.getBool("allow_multi_login");
            if (!multiLogin) {
                // 安全模式（默认）：读者登录时清掉管理端身份（理由同上，不能 invalidate）
                HttpSession old = request.getSession(false);
                if (old != null) {
                    old.removeAttribute("logged_in");
                }
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("reader_id", reader.getId());
            session.setAttribute("reader_name", reader.getName());
            session.setAttribute("reader_card", reader.getCardNo());
            applySessionPolicy(session, remember, response);

            if ("待审核".equals(reader.getStatus())) {
                Flash.warn(ra, "欢迎回来，" + reader.getName() + "。你的办卡申请还在等管理员确认，"
                        + "确认后就能借书了。");
            } else {
                Flash.ok(ra, "欢迎回来，" + reader.getName());
            }
            String target = next != null && next.startsWith("/reader") ? next : "/reader/";
            return "redirect:" + target;
        }
        return "redirect:/login";
    }

    /** 只退出管理端；读者端若同时开着不受影响（与 Python 版一致）。 */
    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes ra) {
        boolean hadReader = session.getAttribute("reader_id") != null;
        session.removeAttribute("logged_in");
        session.setAttribute("_csrf", GlobalModelAdvice.randomHex(16));
        Flash.ok(ra, hadReader ? "已退出管理端（读者端还开着，另一个窗口不受影响）" : "已退出登录");
        return "redirect:/login";
    }

    /** 管理端 + 读者端一起退。 */
    @PostMapping("/logout-all")
    public String logoutAll(HttpSession session, RedirectAttributes ra) {
        session.removeAttribute("logged_in");
        session.removeAttribute("reader_id");
        session.removeAttribute("reader_name");
        session.removeAttribute("reader_card");
        Flash.ok(ra, "已全部退出登录");
        return "redirect:/login";
    }

    @GetMapping("/health")
    @ResponseBody
    public String health() {
        return "ok";
    }

    /** 工作台轮询：近 2 小时内申请重置密码的人数（自动刷新提示用）。 */
    @GetMapping("/api/pending-resets")
    @ResponseBody
    public Map<String, Object> pendingResets() {
        return Map.of("count", auxDao.pendingResetCount());
    }

    /** next 参数只允许站内管理端路径。 */
    private String safeAdminTarget(String next) {
        if (next != null && next.startsWith("/") && !next.startsWith("/reader")) {
            return next;
        }
        return "/";
    }

    /** 写启动戳、重置 CSRF；勾了“记住我”则 7 天免登录（延长会话 Cookie）。 */
    private void applySessionPolicy(HttpSession session, String remember, HttpServletResponse response) {
        session.setAttribute("_boot", bootToken.get());
        session.setAttribute("_csrf", GlobalModelAdvice.randomHex(16));
        boolean rememberMe = "1".equals(remember) || "on".equals(remember);
        if (rememberMe) {
            session.setMaxInactiveInterval(7 * 24 * 3600);
            response.addHeader("Set-Cookie",
                    "JSESSIONID=" + session.getId() + "; Path=/; HttpOnly; Max-Age=" + (7 * 24 * 3600));
        }
    }
}
