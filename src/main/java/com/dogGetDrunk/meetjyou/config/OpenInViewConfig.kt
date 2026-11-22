package com.dogGetDrunk.meetjyou.config

import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Open-in-view as Boot registers it (`spring.jpa.open-in-view` is off so Boot does not add its own),
 * minus the paths that call an outside API after their DB work. Under open-in-view Spring holds the
 * connection until the request ends (DELAYED_ACQUISITION_AND_HOLD), so the place search would keep a
 * pooled connection through the whole 관광공사 call. Pinned by PlaceSearchTourApiIntegrationTest.
 */
@Configuration
class OpenInViewConfig(
    private val entityManagerFactory: EntityManagerFactory,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        val interceptor = OpenEntityManagerInViewInterceptor()
        interceptor.entityManagerFactory = entityManagerFactory
        registry.addWebRequestInterceptor(interceptor).excludePathPatterns(EXCLUDED_PATHS)
    }

    companion object {
        val EXCLUDED_PATHS = listOf("${ApiVersionConfig.V1}/places/search")
    }
}
