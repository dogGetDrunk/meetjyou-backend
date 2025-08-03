package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
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
