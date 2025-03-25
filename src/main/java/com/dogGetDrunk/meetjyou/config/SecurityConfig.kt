package com.dogGetDrunk.meetjyou.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.CorsFilter

@Configuration
class SecurityConfig(
    private val corsConfig: CorsConfig
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .addFilterBefore(
                CorsFilter(corsConfig.corsConfigurationSource()),
                UsernamePasswordAuthenticationFilter::class.java
            ) // CORS 필터 추가
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").authenticated() // Swagger UI 접근 시 인증 요구
                    .anyRequest().permitAll()
            }
            .httpBasic(Customizer.withDefaults()) // Basic Auth 적용
            .cors { cors -> cors.configurationSource(corsConfig.corsConfigurationSource()) }
            .csrf { it.disable() } // CSRF 보호 비활성화 (필요 시 설정)

        return http.build()
    }
}

