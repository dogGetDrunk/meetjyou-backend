package com.dogGetDrunk.meetjyou.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").authenticated() // Swagger UI 접근 시 인증 요구
                .anyRequest().permitAll()
            )
            .httpBasic(withDefaults()) // Basic Auth 적용
            .csrf(AbstractHttpConfigurer::disable); // CSRF 보호 비활성화 (필요 시 설정)

        return http.build();
    }
}
