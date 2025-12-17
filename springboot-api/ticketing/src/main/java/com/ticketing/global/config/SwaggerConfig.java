package com.ticketing.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("티켓팅 시스템 API")
                        .description("대규모 트래픽 처리를 위한 티켓팅 플랫폼 API")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Chang YunYeong")
                                .email("sally0109277@naver.com")));
    }
}