package com.ucamp.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Swagger(springdoc-openapi) 문서 정보. 로컬에서 /swagger-ui.html 로 확인한다.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Thankunnie API")
                        .description("Thankunnie 백엔드 API 명세")
                        .version("v1"));
    }
}
