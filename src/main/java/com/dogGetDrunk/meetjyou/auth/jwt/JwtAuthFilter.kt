package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserWithdrawnException
import com.dogGetDrunk.meetjyou.config.ApiVersionConfig.Companion.V1
import com.dogGetDrunk.meetjyou.config.PublicReadEndpoints
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
    private val authenticatedUserCache: AuthenticatedUserCache,
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
        val proceed = token == null || tryAuthenticate(token, request, response)
        if (proceed) {
            filterChain.doFilter(request, response)
        }
    }

    // Returns whether the request continues down the chain; false means an error response was written.
    private fun tryAuthenticate(
        token: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): Boolean =
        try {
            authenticateFromToken(token, request)
            true
        } catch (e: BusinessException) {
            // A public read answers a failed token like a token-less request: the app may still
            // attach an expired or stale token there, and the endpoint needs no user anyway.
            if (PublicReadEndpoints.matches(request)) {
                SecurityContextHolder.clearContext()
                log.info("Ignoring failed token on public read {}: {}", request.requestURI, e.errorCode)
                true
            } else {
                writeErrorResponse(response, e)
                false
            }
        }

    // Filter-level failures never reach GlobalExceptionHandler, so this mirrors it: any domain
    // exception, status from its ErrorCode. Catching only the JWT family would let a non-JWT
    // exception thrown here escape the filter as a 500.
    private fun writeErrorResponse(response: HttpServletResponse, e: BusinessException) {
        response.status = e.errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, ErrorResponse(e.errorCode, e.value))
    }

    private fun authenticateFromToken(token: String, request: HttpServletRequest) {
        jwtProvider.validateAccessTokenOrThrow(token)

        val userUuid = jwtProvider.getUserUuid(token)
        val user = resolveActiveUser(userUuid)
        // Role comes from the DB row, not the token claim, so a revoked ADMIN loses access well
        // before the access token expires: on the next request for in-app changes, within
        // AuthenticatedUserCache.TTL for SQL changes.
        val authentication = buildAuthentication(user, request)

        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun resolveActiveUser(userUuid: UUID): AuthenticatedUser =
        authenticatedUserCache.findActive(userUuid)
            ?: throw UserWithdrawnException(
                userUuid.toString(),
                message = "Withdrawn or unknown user attempted to use an access token",
            )

    private fun buildAuthentication(
        user: AuthenticatedUser,
        request: HttpServletRequest,
    ): UsernamePasswordAuthenticationToken {
        val principal = CustomUserPrincipal(
            uuid = user.uuid,
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
