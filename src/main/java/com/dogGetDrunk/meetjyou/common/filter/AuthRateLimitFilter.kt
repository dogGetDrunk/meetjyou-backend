package com.dogGetDrunk.meetjyou.common.filter

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

/**
 * Per-IP rate limiting for auth endpoints prone to brute-force/abuse.
 * Buckets are kept in-memory (no Redis in this deployment); this is sufficient
 * for the current single-instance deployment but won't hold a shared limit
 * across multiple instances if the service is scaled horizontally.
 */
@Component
class AuthRateLimitFilter(
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    companion object {
        private val LIMITED_PATHS = mapOf(
            "/api/v1/auth/registration" to 5L,
            "/api/v1/auth/nonce" to 10L,
            "/api/v1/auth/login" to 5L,
            "/api/v1/auth/refresh" to 10L,
        )
        private val WINDOW: Duration = Duration.ofMinutes(1)
    }

    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .maximumSize(50_000)
        .build<String, Bucket>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !LIMITED_PATHS.containsKey(request.requestURI)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val limit = LIMITED_PATHS.getValue(request.requestURI)
        val key = "${request.remoteAddr}:${request.requestURI}"
        val bucket = buckets.get(key) { newBucket(limit) }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = 429
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, ErrorResponse(429, ErrorCode.RATE_LIMIT_EXCEEDED))
    }

    private fun newBucket(limit: Long): Bucket =
        Bucket.builder()
            .addLimit(Bandwidth.classic(limit, Refill.greedy(limit, WINDOW)))
            .build()
}
