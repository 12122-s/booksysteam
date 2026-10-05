package com.library.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * REST 接口的角色要求，标在方法或类上，由 ApiAuthInterceptor 检查。
 * 可选值："admin"、"reader"；不标注 = 任何有效登录身份都可访问。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiRoles {

    String[] value() default {};
}
