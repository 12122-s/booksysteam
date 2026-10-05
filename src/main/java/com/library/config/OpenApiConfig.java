package com.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 文档配置：声明 Bearer JWT 安全方案，
 * Swagger UI（/swagger-ui.html）右上角会出现 Authorize 按钮。
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI apiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("图书管理系统 REST API")
                        .version("v1")
                        .description("先调用 POST /api/v1/auth/login 拿 token，"
                                + "再在 Authorize 里填 Bearer <token>；"
                                + "管理端接口需要 admin 身份。"))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }
}
