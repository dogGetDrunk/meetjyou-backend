package com.dogGetDrunk.meetjyou.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SwaggerSecurityConfig {

    @Bean
    @Order(1)
    fun swaggerFilterChain(
        http: HttpSecurity,
        inMemoryUserDetailsManager: InMemoryUserDetailsManager
    ): SecurityFilterChain {
        return http
            .securityMatcher("/swagger-ui/**", "/v3/api-docs/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .userDetailsService(inMemoryUserDetailsManager) // ✅ 여기서 명시적으로 사용
            .httpBasic { } // 인증창 띄움
            .build()
    }
}
