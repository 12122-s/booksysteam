package com.library.web;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 统一错误页（对应 Python 版 errorhandler 400/404/500）。
 * 关闭 Whitelabel，改成与全站一致的 error.html。
 */
@Controller
public class ErrorPageController implements ErrorController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ErrorPageController.class);

    @RequestMapping("/error")
    public String error(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = statusObj instanceof Integer ? (Integer) statusObj : 500;
        String message;
        switch (code) {
            case 400 -> message = "页面停留时间过长，安全校验已失效。请返回上一页刷新后重新提交。";
            case 404 -> message = "没有找到你要看的页面或数据，它可能已经被删除了。";
            default -> {
                message = "系统遇到了一个小问题，请稍后重试。如果反复出现，请把下面的错误信息反馈给管理员。";
                Object error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                if (error instanceof Throwable t) {
                    log.error("服务器错误", t);
                    message += "（" + t.getClass().getSimpleName() + "）";
                }
            }
        }
        response.setStatus(code);
        model.addAttribute("code", code);
        model.addAttribute("message", message);
        model.addAttribute("status", HttpStatus.valueOf(code));
        return "error";
    }
}
