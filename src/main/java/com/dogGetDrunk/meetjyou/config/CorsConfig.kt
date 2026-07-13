package com.dogGetDrunk.meetjyou.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    @Value("\${dns.url}")
    private val dnsUrl: String,
    private val environment: Environment,
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()

        // localhost origins are only needed for local development against a deployed backend;
        // allowing them in release would let any locally-run browser page make credentialed calls.
        config.allowedOriginPatterns = if (environment.activeProfiles.contains("dev")) {
            listOf(dnsUrl, "http://localhost:*", "http://127.0.0.1:*")
        } else {
            listOf(dnsUrl)
        }

        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)

        return source
    }
}
