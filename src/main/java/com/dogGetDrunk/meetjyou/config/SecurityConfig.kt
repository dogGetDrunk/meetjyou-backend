package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.dev.DevBypassAuthFilter
import com.dogGetDrunk.meetjyou.auth.jwt.JwtAuthFilter
import com.dogGetDrunk.meetjyou.config.ApiVersionConfig.Companion.V1
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val corsConfig: CorsConfig,
    private val jwtAuthFilter: JwtAuthFilter,
    private val devBypassAuthFilterProvider: ObjectProvider<DevBypassAuthFilter>
) {

    @Bean
    fun webSecurityCustomizer(): WebSecurityCustomizer =
        WebSecurityCustomizer { web ->
            web.ignoring().requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-doc/**")
        }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfig.corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Infrastructure
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/ws-chat", "/ws-chat/**").permitAll()
                    .requestMatchers("/pub/**", "/sub/**").permitAll()
                    // Swagger — API 스펙만 노출, 데이터 없음
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-doc/**").permitAll()
                    // Auth — no token required (promote-admin requires auth, handled by anyRequest)
                    .requestMatchers("$V1/auth/registration").permitAll()
                    .requestMatchers("$V1/auth/nonce").permitAll()
                    .requestMatchers("$V1/auth/login").permitAll()
                    .requestMatchers("$V1/auth/refresh").permitAll()
                    .requestMatchers("$V1/auth/logout").permitAll()
                    .requestMatchers("$V1/dev/auth/**").permitAll()
                    // Public read-only
                    .requestMatchers(HttpMethod.GET, "$V1/notices/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/terms/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/*/versions/check").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/*/versions/latest").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/users/is-duplicate-nickname").permitAll()
                    // All remaining endpoints require a valid JWT
                    .anyRequest().authenticated()
            }

        devBypassAuthFilterProvider.ifAvailable?.let { devBypassFilter ->
            http.addFilterBefore(devBypassFilter, UsernamePasswordAuthenticationFilter::class.java)
        }

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
