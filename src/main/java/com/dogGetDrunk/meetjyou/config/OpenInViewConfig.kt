package com.dogGetDrunk.meetjyou.config

import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Open-in-view as Boot registers it (`spring.jpa.open-in-view` is off so Boot does not add its own),
 * minus the paths that call an outside API after their DB work. Under open-in-view Spring holds the
 * first connection a request uses until the request ends (DELAYED_ACQUISITION_AND_HOLD), so such a
 * path would keep a pooled connection through the whole outside call, short transactions or not.
 *
 * Rule for a new path: exclude it only when it touches the DB before the outside call, finish its DB
 * work inside transactions (lazy loading outside one fails once excluded), and add a test that reads
 * zero active connections during the call. A path whose outside call comes before any DB access holds
 * nothing and stays under open-in-view.
 *
 * Excluded today: the place search (관광공사) and the OCI image routes of OracleObjectStorageController.
 * Pinned by OpenInViewExclusionTest, PlaceSearchTourApiIntegrationTest and ImageParConnectionIntegrationTest.
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
        // Narrow on purpose: PUT /parties/{uuid}/img/confirm (PartyController) makes no outside call.
        private val IMAGE_PATHS = listOf(
            "/users/*/img/profile",
            "/users/*/img/profile/**",
            "/users/img/**",
            "/posts/*/img",
            "/posts/*/img/par/**",
            "/posts/*/img/original/**",
            "/posts/img/**",
            "/parties/*/img",
            "/parties/*/img/par/**",
            "/parties/*/img/original/**",
            "/parties/img/**",
        )

        val EXCLUDED_PATHS = listOf("/places/search").plus(IMAGE_PATHS).map { "${ApiVersionConfig.V1}$it" }
    }
}
