package com.dogGetDrunk.meetjyou.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerReleaseConfig(
    @Value("\${dns.url}")
    private val dnsUrl: String
) {
    // JWT를 사용하는 경우
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .addServersItem(Server().url(dnsUrl))
            .info(
                Info()
                    .title("만나쥬 API")
                    .version("1.0.0")
                    .description("만나쥬 API 문서")
            )
            .addSecurityItem(SecurityRequirement().addList("Bearer Authentication"))
            .components(
                Components()
                    .addSecuritySchemes(
                        "Bearer Authentication",
                        SecurityScheme()
                            .name("Authorization")
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }
}
