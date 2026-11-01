package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.source.JWKSetSource
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.jwk.source.RateLimitReachedException
import com.nimbusds.jose.jwk.source.URLBasedJWKSetSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import java.net.URI
import java.time.Duration

class KakaoJwkSource private constructor(
    private val delegate: JWKSource<SecurityContext>,
) : JWKSource<SecurityContext> {
    override fun get(selector: JWKSelector, context: SecurityContext?): List<JWK> = try {
        delegate.get(selector, context)
    } catch (_: RateLimitReachedException) {
        // Reject unknown keys without turning forged-kid traffic into provider outages.
        emptyList()
    }

    companion object {
        val OUTAGE_TOLERANCE: Duration = Duration.ofHours(24)
        val RATE_LIMIT_INTERVAL: Duration = Duration.ofSeconds(30)
        private val REFRESH_TIMEOUT: Duration = Duration.ofSeconds(15)
        private val HTTP_TIMEOUT: Duration = Duration.ofSeconds(3)
        private const val HTTP_SIZE_LIMIT_BYTES = 50 * 1024

        fun create(props: KakaoOidcProperties): KakaoJwkSource {
            val retriever = DefaultResourceRetriever(
                HTTP_TIMEOUT.toMillis().toInt(), HTTP_TIMEOUT.toMillis().toInt(), HTTP_SIZE_LIMIT_BYTES,
            )
            val origin = URLBasedJWKSetSource<SecurityContext>(URI(props.jwksUri).toURL(), retriever)
            return create(origin, Duration.ofMinutes(props.jwkCacheTtlMinutes))
        }

        // Injectable durations allow outage tests without contacting Kakao.
        fun create(
            origin: JWKSetSource<SecurityContext>,
            cacheTtl: Duration,
            outageTolerance: Duration = OUTAGE_TOLERANCE,
            rateLimitInterval: Duration = RATE_LIMIT_INTERVAL,
        ): KakaoJwkSource {
            val source = JWKSourceBuilder.create(origin)
                .cache(cacheTtl.toMillis(), REFRESH_TIMEOUT.toMillis())
                .refreshAheadCache(false)
                .rateLimited(rateLimitInterval.toMillis())
                .outageTolerant(outageTolerance.toMillis())
                .build()
            return KakaoJwkSource(source)
        }
    }
}
