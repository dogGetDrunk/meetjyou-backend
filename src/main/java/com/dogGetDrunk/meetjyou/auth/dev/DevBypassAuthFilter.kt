package com.dogGetDrunk.meetjyou.auth.dev

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.config.property.DevBypassProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

class DevBypassAuthFilter(
    private val props: DevBypassProperties,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(DevBypassAuthFilter::class.java)

    private val matcher = OrRequestMatcher(
        AntPathRequestMatcher("/api/v1/parties/**"),
        AntPathRequestMatcher("/api/v1/posts/**"),
        AntPathRequestMatcher("/api/v1/plans/**")
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!props.enabled) return true
        return !matcher.matches(request)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val context = SecurityContextHolder.getContext()

        // 1) 이미 인증이 있으면 건드리지 않음
        if (context.authentication?.isAuthenticated == true) {
            filterChain.doFilter(request, response)
            return
        }

        // 2) Authorization 헤더가 있으면 JWT가 처리하게 둠
        val authHeader = request.getHeader("Authorization")
        if (!authHeader.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        // 3) dev bypass: 관리자 인증 주입
        val devAdminUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val authorities = listOf(
            SimpleGrantedAuthority("ADMIN"),
//            SimpleGrantedAuthority("TOOLING_READ"),
//            SimpleGrantedAuthority("TOOLING_WRITE"),
//            SimpleGrantedAuthority("TOOLING_IMPERSONATE")
        )

        val principal = CustomUserPrincipal(
            uuid = devAdminUuid,
            email = "dev-admin@local",
            authorities = authorities
        )

        val authentication = UsernamePasswordAuthenticationToken(
            principal,
            null,
            authorities
        ).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request)
        }

        context.authentication = authentication

        log.warn(
            "[DEV-BYPASS] Injected admin authentication. uri={}, method={}",
            request.requestURI,
            request.method
        )

        filterChain.doFilter(request, response)
    }
}
