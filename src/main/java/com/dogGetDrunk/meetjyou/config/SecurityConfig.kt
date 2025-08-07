package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.jwt.JwtAuthFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val corsConfig: CorsConfig,
    private val jwtAuthFilter: JwtAuthFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfig.corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").authenticated()
                    .anyRequest().permitAll() // TODO: 이후 protected endpoint 설정
            }
            .addFilterBefore(
                jwtAuthFilter, // 실제 JWT 인증 필터
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}
