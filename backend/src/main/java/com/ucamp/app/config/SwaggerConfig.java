package com.ucamp.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springdoc.core.utils.SpringDocUtils;

import com.ucamp.app.common.auth.LoginUser;
import com.ucamp.app.common.auth.StubAuthFilter;

/**
 * Swagger(springdoc-openapi) 문서 정보. 로컬에서 /swagger-ui.html 로 확인한다.
 */
@Configuration
public class SwaggerConfig {

    private static final String TEST_USER_SCHEME = "testUser";
    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // @LoginUser 파라미터는 서버가 인증 정보로 채우므로 요청 파라미터로 문서화하지 않는다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginUser.class);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Thankunnie API")
                        .description("Thankunnie 백엔드 API 명세")
                        .version("v1"))
                // Authorize 버튼에서 둘 중 하나를 넣으면 모든 요청에 헤더가 붙는다.
                // bearerAuth: 로그인 API 응답의 accessToken, testUser: X-Test-User 값 (local 전용)
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("POST /api/v1/auth/login 또는 /api/v1/auth/shops/login 응답의 accessToken"))
                        .addSecuritySchemes(TEST_USER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(StubAuthFilter.HEADER_NAME)
                                .description("CUSTOMER_1, CUSTOMER_2, OWNER_1, OWNER_2, ADMIN")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(TEST_USER_SCHEME));
    }
}
