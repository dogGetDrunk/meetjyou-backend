package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.config.ApiVersionConfig.Companion.V1
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpMethod
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.util.UrlPathHelper

/**
 * Public read-only endpoints: permitAll in SecurityConfig, and JwtAuthFilter lets a failed bearer
 * token through anonymously here so the response matches a token-less request.
 * Single source for both, so the two lists can't drift apart.
 */
object PublicReadEndpoints {

    private val PATTERNS = listOf(
        "$V1/notices/**",
        "$V1/terms/**",
        "$V1/*/versions/check",
        "$V1/*/versions/latest",
        "$V1/users/is-duplicate-nickname",
    )

    // Matches on requestURI via UrlPathHelper: the 2-arg/antMatcher form builds the path from
    // servletPath + pathInfo only, which is empty for requests built without a servlet mapping.
    val matchers: List<RequestMatcher> = PATTERNS.map { pattern ->
        AntPathRequestMatcher(pattern, HttpMethod.GET.name(), true, UrlPathHelper.defaultInstance)
    }

    val requestMatcher: RequestMatcher = OrRequestMatcher(matchers)

    fun matches(request: HttpServletRequest): Boolean = requestMatcher.matches(request)
}
