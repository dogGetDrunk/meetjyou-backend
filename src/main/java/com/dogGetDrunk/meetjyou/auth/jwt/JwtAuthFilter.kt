package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(
    private val jwtProvider: JwtProvider,
) : OncePerRequestFilter() {

    val log = LoggerFactory.getLogger(JwtAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        /*
        스웨거 문서 경로는 JWT 인증 필터를 우회하도록 설정합니다.
         */
        val path = request.requestURI

        log.info("JwtAuthFilter invoked for $path")
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            log.info("Skipping JWT filter for Swagger path: $path")
            filterChain.doFilter(request, response)
            return
        }
        /*
        우회 종료
         */

        val token = jwtProvider.extractToken(request)

        if (token != null && jwtProvider.validateToken(token)) {
            val userUuid: UUID = jwtProvider.getUserUuid(token)
            val email: String = jwtProvider.getUsername(token)

            // CustomUserPrincipal 생성
            val principal = CustomUserPrincipal(userUuid, email)

            // 인증 객체 구성
            val authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities
            ).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }

            // SecurityContextHolder에 주입
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }
}
