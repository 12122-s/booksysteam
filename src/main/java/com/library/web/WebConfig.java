package com.library.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web 配置：注册页面登录/CSRF 拦截器与 REST API 的 JWT 拦截器。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ApiAuthInterceptor apiAuthInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, ApiAuthInterceptor apiAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.apiAuthInterceptor = apiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 页面端：会话登录 + CSRF
        registry.addInterceptor(authInterceptor).addPathPatterns("/**");
        // REST API：JWT。auth/login 是获取 token 的入口，不拦
        registry.addInterceptor(apiAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/login");
    }
}
