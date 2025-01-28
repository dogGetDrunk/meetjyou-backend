package com.dogGetDrunk.meetjyou.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // JWT를 사용하지 않는 경우
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(apiInfo());
    }

    private Info apiInfo() {
        return new Info()
                .title("API Test") // API의 제목
                .description("만나쥬 API 문서") // API에 대한 설명
                .version("1.0.0"); // API의 버전
    }

    // JWT를 사용하는 경우
//    @Bean
//    public OpenAPI openAPI() {
//        String jwt = "JWT";
//        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwt);
//        Components components = new Components().addSecuritySchemes(jwt, new SecurityScheme()
//                .name(jwt)
//                .type(SecurityScheme.Type.HTTP)
//                .scheme("bearer")
//                .bearerFormat("JWT")
//        );
//        return new OpenAPI()
//                .components(new Components())
//                .info(apiInfo())
//                .addSecurityItem(securityRequirement)
//                .components(components);
//    }
}
