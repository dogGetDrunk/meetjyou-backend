package com.dogGetDrunk.meetjyou.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    @Value("\${dns.url}")
    private val dnsUrl: String
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()

        config.allowedOrigins = listOf(dnsUrl)

        // 허용할 HTTP 메서드
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")

        // HTTP 요청 헤더 허용
        config.allowedHeaders = listOf("*")

        // 요청이 Credentials (쿠키, Authorization 헤더 등)을 포함할 수 있도록 허용
        config.allowCredentials = true

        // 모든 엔드포인트에 대해 CORS 설정 적용
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)

        return source
    }
}
