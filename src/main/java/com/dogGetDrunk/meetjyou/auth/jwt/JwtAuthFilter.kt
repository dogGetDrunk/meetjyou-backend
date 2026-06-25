package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(
    private val jwtProvider: JwtProvider,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    val log = LoggerFactory.getLogger(JwtAuthFilter::class.java)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path == "/actuator/health"
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val existing = SecurityContextHolder.getContext().authentication
        if (existing != null && existing.isAuthenticated) {
            filterChain.doFilter(request, response)
            return
        }

        log.info("JwtAuthFilter invoked for ${request.requestURI}")

        val token = jwtProvider.extractToken(request)

        if (token != null) {
            try {
                jwtProvider.validateTokenOrThrow(token)
            } catch (e: CustomJwtException) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                objectMapper.writeValue(response.writer, ErrorResponse(401, e.errorCode, e.value))
                return
            }

            val userUuid: UUID = jwtProvider.getUserUuid(token)
            val email: String = jwtProvider.getUsername(token)
            val role = jwtProvider.getRole(token)

            val principal = CustomUserPrincipal(
                uuid = userUuid,
                email = email,
                authorities = listOf(SimpleGrantedAuthority(role.name))
            )

            val authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities
            ).apply {
                details = WebAuthenticationDetailsSource().buildDetails(request)
            }

            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }
}
