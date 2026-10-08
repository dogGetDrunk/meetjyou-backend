package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.UserWithdrawnException
import com.dogGetDrunk.meetjyou.config.ApiVersionConfig.Companion.V1
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

    // Every /auth endpoint is public and authenticates with its own credential (provider token,
    // refresh token in the body), so an Authorization header there is ignored: an app interceptor
    // that attaches an expired access token must still be able to refresh. Fail-closed: an
    // endpoint under /auth that requires authentication gets no principal here and is rejected
    // by anyRequest().authenticated().
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path == "/actuator/health"
            || path.startsWith("/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith(AUTH_ENDPOINTS_PREFIX)
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
        jwtProvider.validateAccessTokenOrThrow(token)

        val userUuid = jwtProvider.getUserUuid(token)
        val user = resolveActiveUser(userUuid)
        // Role comes from the DB row already loaded here, not the token claim, so a revoked
        // ADMIN loses access on the next request instead of when the access token expires.
        val authentication = buildAuthentication(user, request)

        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun resolveActiveUser(userUuid: UUID): User {
        val user = userRepository.findByUuid(userUuid)
        if (user == null || user.status == UserStatus.DELETED) {
            throw UserWithdrawnException(userUuid.toString(), message = "Withdrawn or unknown user attempted to use an access token")
        }
        return user
    }

    private fun buildAuthentication(user: User, request: HttpServletRequest): UsernamePasswordAuthenticationToken {
        val principal = CustomUserPrincipal(
            uuid = user.uuid,
            email = user.email,
            authorities = listOf(SimpleGrantedAuthority(user.role.name)),
        )
        return UsernamePasswordAuthenticationToken(principal, null, principal.authorities).apply {
            details = WebAuthenticationDetailsSource().buildDetails(request)
        }
    }

    companion object {
        private const val AUTH_ENDPOINTS_PREFIX = "$V1/auth/"
    }
}
