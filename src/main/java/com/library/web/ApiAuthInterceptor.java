package com.library.web;

import java.io.IOException;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.library.util.JwtUtil;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST API 认证拦截：保护 /api/v1/**（登录接口本身除外）。
 *
 * <p>凭据：Authorization: Bearer &lt;jwt&gt;。解析通过后把身份放进请求属性
 * （apiRole / apiName / apiReaderId），控制器用 @RequestAttribute 读取；
 * 未登录返回 401，角色不满足（@ApiRoles）返回 403，全部输出 JSON。
 */
@Component
public class ApiAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_ROLE = "apiRole";
    public static final String ATTR_NAME = "apiName";
    public static final String ATTR_READER_ID = "apiReaderId";

    private final JwtUtil jwtUtil;

    public ApiAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String token = bearerToken(request);
        if (token == null) {
            return reject(response, 401, "未登录：请在 Authorization 头携带 Bearer token");
        }
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            return reject(response, 401, "登录状态无效或已过期，请重新获取 token");
        }

        String role = claims.get("role", String.class);
        request.setAttribute(ATTR_ROLE, role);
        request.setAttribute(ATTR_NAME, claims.get("name", String.class));
        Integer readerId = claims.get("readerId", Integer.class);
        if (readerId != null) {
            request.setAttribute(ATTR_READER_ID, readerId);
        }

        // 角色检查：方法上的 @ApiRoles 优先，其次类上的
        if (handler instanceof HandlerMethod handlerMethod) {
            ApiRoles required = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getMethod(), ApiRoles.class);
            if (required == null) {
                required = AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), ApiRoles.class);
            }
            if (required != null && required.value().length > 0) {
                boolean allowed = false;
                for (String r : required.value()) {
                    if (r.equals(role)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    return reject(response, 403, "权限不足：该接口需要 " + String.join("/", required.value()) + " 身份");
                }
            }
        }
        return true;
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\""
                + jsonEscape(message) + "\"}");
        return false;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
