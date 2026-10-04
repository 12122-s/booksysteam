package com.library.web;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.library.dao.AuxDao;
import com.library.service.SettingsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 公共模板变量（对应 Python 版 @app.context_processor inject_globals）：
 * 每个页面都能拿到系统设置、导航徽标数、CSRF 令牌等。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final SettingsService settings;
    private final AuxDao auxDao;
    private final com.library.dao.BorrowDao borrowDao;
    private final com.library.dao.BorrowRequestDao requestDao;
    private final com.library.service.BorrowService borrowService;

    public GlobalModelAdvice(SettingsService settings, AuxDao auxDao,
                             com.library.dao.BorrowDao borrowDao,
                             com.library.dao.BorrowRequestDao requestDao,
                             com.library.service.BorrowService borrowService) {
        this.settings = settings;
        this.auxDao = auxDao;
        this.borrowDao = borrowDao;
        this.requestDao = requestDao;
        this.borrowService = borrowService;
    }

    @ModelAttribute
    public void injectGlobals(HttpServletRequest request, HttpSession session, Model model) {
        model.addAttribute("conf", settings.all());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("csrfToken", csrfToken(session));
        model.addAttribute("announcement",
                settings.get("announcement") == null ? "" : settings.get("announcement").trim());

        // 导航徽标：逾期 / 待处理重置 / 未收罚金 / 待办理申请（查失败不影响页面）
        int navOverdue = 0;
        int navResets = 0;
        int navUnpaidFines = 0;
        int navRequests = 0;
        try {
            navOverdue = borrowDao.countOverdue(LocalDate.now().toString());
            navResets = auxDao.pendingResetCount();
            navUnpaidFines = borrowDao.countUnpaidFines();
            navRequests = requestDao.countPending();
        } catch (Exception ignored) {
            // 数据库还没建好时徽标归零即可
        }
        model.addAttribute("navOverdue", navOverdue);
        model.addAttribute("navResets", navResets);
        model.addAttribute("navUnpaidFines", navUnpaidFines);
        model.addAttribute("navRequests", navRequests);
        model.addAttribute("isReader", session.getAttribute("reader_id") != null);
        model.addAttribute("isAdmin", Boolean.TRUE.equals(session.getAttribute("logged_in")));
        model.addAttribute("sessionReaderName", session.getAttribute("reader_name"));
        model.addAttribute("sessionReaderCard", session.getAttribute("reader_card"));
        model.addAttribute("pu", new PageUrl());
    }

    /**
     * 翻页链接助手：保留当前查询条件，只替换 page 参数
     * （对应 Python 版模板全局 page_url()）。
     */
    public static class PageUrl {
        public String of(int targetPage) {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "?page=" + targetPage;
            }
            HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes) attrs).getRequest();
            Map<String, String[]> params = new java.util.LinkedHashMap<>(request.getParameterMap());
            params.remove("page");
            StringBuilder sb = new StringBuilder(request.getRequestURI());
            boolean first = true;
            for (var entry : params.entrySet()) {
                for (String v : entry.getValue()) {
                    sb.append(first ? '?' : '&')
                            .append(urlEncode(entry.getKey())).append('=')
                            .append(urlEncode(v == null ? "" : v));
                    first = false;
                }
            }
            sb.append(first ? '?' : '&').append("page=").append(targetPage);
            return sb.toString();
        }

        private static String urlEncode(String value) {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** 会话里的 CSRF 令牌；没有就生成一个。 */
    public static String csrfToken(HttpSession session) {
        String token = (String) session.getAttribute("_csrf");
        if (token == null || token.isBlank()) {
            token = randomHex(16);
            session.setAttribute("_csrf", token);
        }
        return token;
    }

    public static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new java.security.SecureRandom().nextBytes(buf);
        return java.util.HexFormat.of().formatHex(buf);
    }

    /** 页面里常用的金额格式化助手（模板里用 ${money(x)} 调用）。 */
    @ModelAttribute("money")
    public MoneyFormatter moneyFormatter() {
        return new MoneyFormatter();
    }

    public static class MoneyFormatter {
        public String of(Object value) {
            double v = 0;
            if (value instanceof Number n) {
                v = n.doubleValue();
            } else if (value != null) {
                try {
                    v = Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    return "0.00";
                }
            }
            return String.format("%.2f", v);
        }
    }
}
