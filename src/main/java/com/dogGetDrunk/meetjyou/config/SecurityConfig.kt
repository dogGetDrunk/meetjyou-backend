package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.dev.DevBypassAuthFilter
import com.dogGetDrunk.meetjyou.auth.jwt.JwtAuthFilter
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.filter.AuthRateLimitFilter
import com.dogGetDrunk.meetjyou.config.ApiVersionConfig.Companion.V1
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
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
    private val authRateLimitFilter: AuthRateLimitFilter,
    private val devBypassAuthFilterProvider: ObjectProvider<DevBypassAuthFilter>,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    @Order(1)
    fun managementSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
        return http.build()
    }

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
                    // Load-test synthetic token issuance — gated by a static secret header, not JWT
                    .requestMatchers(HttpMethod.POST, "$V1/internal/load-test-token").permitAll()
                    // Public read-only
                    .requestMatchers(HttpMethod.GET, "$V1/notices/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/terms/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/*/versions/check").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/*/versions/latest").permitAll()
                    .requestMatchers(HttpMethod.GET, "$V1/users/is-duplicate-nickname").permitAll()
                    // All remaining endpoints require a valid JWT
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    objectMapper.writeValue(
                        response.writer,
                        ErrorResponse(401, ErrorCode.MISSING_AUTHORIZATION_HEADER)
                    )
                }
            }

        devBypassAuthFilterProvider.ifAvailable?.let { devBypassFilter ->
            http.addFilterBefore(devBypassFilter, UsernamePasswordAuthenticationFilter::class.java)
        }

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        http.addFilterBefore(authRateLimitFilter, JwtAuthFilter::class.java)

        return http.build()
    }
}
