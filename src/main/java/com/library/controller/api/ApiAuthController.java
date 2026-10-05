package com.library.controller.api;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.library.dao.ReaderDao;
import com.library.dto.LoginForm;
import com.library.entity.Reader;
import com.library.service.LoginContext;
import com.library.service.SettingsService;
import com.library.util.JwtUtil;
import com.library.util.PasswordUtil;
import com.library.web.ApiAuthInterceptor;
import com.library.web.ApiRoles;
import com.library.web.LoginRateLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST API 认证：POST /api/v1/auth/login 换取 JWT；GET /api/v1/me 看当前身份。
 * 凭证规则与页面登录完全一致（管理员账号或读者卡号/手机号）。
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "认证", description = "获取 token、查看当前身份")
public class ApiAuthController {

    private final SettingsService settings;
    private final ReaderDao readerDao;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;
    private final com.library.service.LoginContext loginContext;

    public ApiAuthController(SettingsService settings, ReaderDao readerDao, JwtUtil jwtUtil,
                             LoginRateLimiter rateLimiter, com.library.service.LoginContext loginContext) {
        this.settings = settings;
        this.readerDao = readerDao;
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
        this.loginContext = loginContext;
    }

    public record LoginResponse(String token, String tokenType, String role, String name, Integer readerId) {
    }

    @PostMapping("/auth/login")
    @Operation(summary = "登录换取 JWT", description = "管理员用管理员账号，读者用借书卡号或手机号")
    public ResponseEntity<?> login(@Valid @RequestBody LoginForm form, BindingResult bindingResult,
                                   HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message",
                    bindingResult.getFieldError().getDefaultMessage()));
        }
        String account = form.getAccount().trim();
        String ip = loginContext.clientIp();
        long now = System.currentTimeMillis();
        if (rateLimiter.isBlocked(account, ip, now)) {
            return ResponseEntity.status(429)
                    .body(Map.of("code", 429, "message", "登录尝试次数过多，请 15 分钟后再试"));
        }

        String adminAccount = settings.get("admin_account");
        if (adminAccount == null || adminAccount.isBlank()) {
            adminAccount = "admin";
        }

        // ① 管理员
        if (account.equalsIgnoreCase(adminAccount.trim())) {
            String hashed = settings.getAdminPasswordHash();
            if (!hashed.isEmpty() && PasswordUtil.check(hashed, form.getPassword())) {
                rateLimiter.reset(account, ip);
                String token = jwtUtil.create("admin", "admin", "管理员", null);
                return ResponseEntity.ok(new LoginResponse(token, "Bearer", "admin", "管理员", null));
            }
            rateLimiter.recordFailure(account, ip, now);
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "账号或密码不正确"));
        }

        // ② 读者
        Reader reader = readerDao.findByAccount(account);
        boolean ok = reader != null
                && reader.getPasswordHash() != null && !reader.getPasswordHash().isBlank()
                && !"停用".equals(reader.getStatus())
                && PasswordUtil.check(reader.getPasswordHash(), form.getPassword());
        if (!ok) {
            rateLimiter.recordFailure(account, ip, now);
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "账号或密码不正确"));
        }
        rateLimiter.reset(account, ip);
        String token = jwtUtil.create(reader.getCardNo(), "reader", reader.getName(), reader.getId());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", "reader", reader.getName(), reader.getId()));
    }

    @GetMapping("/me")
    @ApiRoles({"admin", "reader"})
    @Operation(summary = "当前登录身份")
    public Map<String, Object> me(@RequestAttribute(ApiAuthInterceptor.ATTR_ROLE) String role,
                                  @RequestAttribute(ApiAuthInterceptor.ATTR_NAME) String name,
                                  @RequestAttribute(value = ApiAuthInterceptor.ATTR_READER_ID,
                                          required = false) Integer readerId) {
        Map<String, Object> result = new HashMap<>();
        result.put("role", role);
        result.put("name", name);
        if (readerId != null) {
            result.put("readerId", readerId);
        }
        return result;
    }
}
