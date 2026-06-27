package com.dogGetDrunk.meetjyou.common.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("ACCESS")

    companion object {
        private const val SLOW_THRESHOLD_MS = 500L
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/actuator/") ||
            uri.startsWith("/swagger-ui/") ||
            uri.startsWith("/v3/api-docs")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val start = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - start
            val msg = "${request.method} ${request.requestURI} -> ${response.status} (${duration}ms)"
            if (duration >= SLOW_THRESHOLD_MS) {
                log.warn("[SLOW] $msg")
            } else {
                log.info(msg)
            }
        }
    }
}
