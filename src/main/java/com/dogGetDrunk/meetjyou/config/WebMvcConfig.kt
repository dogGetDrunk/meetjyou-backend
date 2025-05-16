package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.jwt.JwtAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val jwtAuthInterceptor: JwtAuthInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(jwtAuthInterceptor)
            .addPathPatterns("/api/**") // 인증이 필요한 기본 경로
            .excludePathPatterns(
                "/api/v1/auth/**",
                "/swagger-ui/**",       // Swagger UI 문서
                "/v3/api-docs/**"       // OpenAPI 문서
            )
    }
}
