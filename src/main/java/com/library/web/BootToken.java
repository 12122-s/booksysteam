package com.library.web;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 系统启动时间戳：每次重启生成一个新值存进应用上下文。
 * 用户会话里记录自己登录时的启动戳，两边不一致说明系统重启过，
 * 旧登录状态自动作废、回到登录页（与 Python 版 APP_SESSION_TOKEN 行为一致）。
 */
@Component
public class BootToken {

    private final String token = com.library.web.GlobalModelAdvice.randomHex(8);

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        // 仅用于日志观察
        org.slf4j.LoggerFactory.getLogger(BootToken.class).info("系统已启动（会话启动戳 {}）", token);
    }

    public String get() {
        return token;
    }
}
