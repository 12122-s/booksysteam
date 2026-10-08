package com.library.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.library.service.SettingsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 统一拦截：
 * - 管理端路径（/reader/ 之外）需要管理员登录；
 * - 读者端路径（/reader/ 开头）需要读者登录；
 * - POST 表单必须带 CSRF 校验令牌（session 里存的 _csrf）。
 * 公开页（登录、公告、健康检查等）放行。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AuthInterceptor.class);

    /** 无需登录的路径前缀/精确路径。 */
    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "/login", "/logout", "/logout-all", "/health", "/notice", "/error",
            "/reader/login", "/reader/forgot", "/reader/reset", "/reader/register", "/reader/signup");

    private final SettingsService settings;
    private final BootToken bootToken;

    public AuthInterceptor(SettingsService settings, BootToken bootToken) {
        this.settings = settings;
        this.bootToken = bootToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        HttpSession session = request.getSession();
        // 系统重启过：上次启动时的登录状态全部作废，回到统一登录页。
        // 只清身份属性而不 invalidate 会话（Tomcat 在同一请求里销毁再建会话不会重发 Cookie）
        if (!bootToken.get().equals(session.getAttribute("_boot"))) {
            session.removeAttribute("logged_in");
            session.removeAttribute("reader_id");
            session.removeAttribute("reader_name");
            session.removeAttribute("reader_card");
            session.setAttribute("_boot", bootToken.get());
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // REST API 走 JWT 拦截器（ApiAuthInterceptor），页面会话体系不管它
        if (path.startsWith("/api/")) {
            return true;
        }
        boolean loggedIn = Boolean.TRUE.equals(session.getAttribute("logged_in"));
        boolean readerIn = session.getAttribute("reader_id") != null;

        boolean isPublic = PUBLIC_GET_PATHS.contains(path)
                || path.startsWith("/static")
                || path.startsWith("/css") || path.startsWith("/js") || path.startsWith("/img")
                || path.startsWith("/uploads")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || (path.equals("/reader/logout"));
        // 公告页是公开的；favicon 404 不拦截
        isPublic = isPublic || path.equals("/favicon.ico");

        if (!isPublic) {
            // 读者端区域：/reader 和 /reader/xxx。注意 /readers 是管理端的读者管理，
            // 不能用 startsWith("/reader") 判断（会把 /readers 误伤）
            boolean readerArea = path.equals("/reader") || path.startsWith("/reader/");
            if (readerArea) {
                if (!readerIn) {
                    response.sendRedirect("/login?next=" + urlEncode(path + queryOf(request)));
                    return false;
                }
            } else if (!loggedIn) {
                if ("GET".equals(request.getMethod())) {
                    response.sendRedirect("/login?next=" + urlEncode(path + queryOf(request)));
                } else {
                    response.sendRedirect("/login");
                }
                return false;
            }
        }

        if ("POST".equals(request.getMethod())) {
            // 退出登录属于安全操作，不做令牌校验，保证任何时候都能退出来
            boolean isLogout = path.equals("/logout") || path.equals("/logout-all")
                    || path.equals("/reader/logout");
            if (!isLogout) {
                String token = request.getParameter("csrf_token");
                if (token == null || token.isBlank()) {
                    token = request.getHeader("X-CSRF-Token");
                }
                String stored = (String) session.getAttribute("_csrf");
                if (token == null || stored == null || !token.equals(stored)) {
                    log.warn("CSRF 校验失败，已拒绝：{} {}（IP {}）",
                            request.getMethod(), path, request.getRemoteAddr());
                    response.sendError(400, "安全校验失败：请返回上一页刷新后重新提交。");
                    return false;
                }
            }
        }
        return true;
    }

    private String queryOf(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isBlank() ? "" : "?" + query;
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
