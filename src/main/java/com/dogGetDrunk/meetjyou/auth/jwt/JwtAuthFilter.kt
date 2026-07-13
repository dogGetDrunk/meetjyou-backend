package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.UserWithdrawnException
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
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
    private val userRepository: UserRepository,
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
                authenticateFromToken(token, request)
            } catch (e: CustomJwtException) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                objectMapper.writeValue(response.writer, ErrorResponse(401, e.errorCode, e.value))
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun authenticateFromToken(token: String, request: HttpServletRequest) {
        jwtProvider.validateTokenOrThrow(token)

        val userUuid = jwtProvider.getUserUuid(token)
        val user = resolveActiveUser(userUuid)
        val authentication = buildAuthentication(user.uuid, jwtProvider.getUsername(token), jwtProvider.getRole(token), request)

        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun resolveActiveUser(userUuid: UUID): User {
        val user = userRepository.findByUuid(userUuid)
        if (user == null || user.status == UserStatus.DELETED) {
            throw UserWithdrawnException(userUuid.toString(), message = "Withdrawn or unknown user attempted to use an access token")
        }
        return user
    }

    private fun buildAuthentication(
        userUuid: UUID,
        email: String,
        role: Role,
        request: HttpServletRequest,
    ): UsernamePasswordAuthenticationToken {
        val principal = CustomUserPrincipal(
            uuid = userUuid,
            email = email,
            authorities = listOf(SimpleGrantedAuthority(role.name)),
        )
        return UsernamePasswordAuthenticationToken(principal, null, principal.authorities).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request)
        }
    }
}
