package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.auth.dev.DevBypassAuthFilter
import com.dogGetDrunk.meetjyou.auth.jwt.JwtAuthFilter
import com.dogGetDrunk.meetjyou.config.ApiVersionConfig.Companion.V1
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val corsConfig: CorsConfig,
    private val jwtAuthFilter: JwtAuthFilter,
    private val devBypassAuthFilterProvider: ObjectProvider<DevBypassAuthFilter>
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfig.corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").authenticated()
                    .requestMatchers("$V1/push-tokens").authenticated()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/ws-chat", "/ws-chat/**").permitAll()
                    .requestMatchers("/pub/**", "/sub/**").permitAll()
                    .anyRequest().permitAll() // TODO: 이후 protected endpoint 설정
            }

        devBypassAuthFilterProvider.ifAvailable?.let { devBypassFilter ->
            http.addFilterBefore(devBypassFilter, UsernamePasswordAuthenticationFilter::class.java)
        }

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
