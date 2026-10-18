package com.dogGetDrunk.meetjyou.auth.social.apple

import com.nimbusds.jose.KeySourceException
import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.KeyType
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.JWKSetSource
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.jwk.source.RateLimitReachedException
import com.nimbusds.jose.jwk.source.URLBasedJWKSetSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

/**
 * Apple JWKS lookup backed by Nimbus [JWKSourceBuilder].
 *
 * The whole key set is cached as one entry, so an expired TTL never drops keys on its own:
 * - refresh failure: the last fetched set keeps serving for [OUTAGE_TOLERANCE] (stale-while-error)
 * - unknown kid: re-fetch at most twice per [RATE_LIMIT_INTERVAL], so forged kids can't amplify calls to Apple
 */
class AppleJwksCache(
    private val jwkSource: JWKSource<SecurityContext>,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Returns null for an unknown kid (caller rejects the token as 401).
     * Throws [IllegalStateException] when no key set is available at all, so a provider outage
     * surfaces as a server error instead of being disguised as an invalid token.
     */
    fun getRsaKeyByKid(kid: String): RSAKey? {
        val selector = JWKSelector(JWKMatcher.Builder().keyID(kid).keyType(KeyType.RSA).build())
        val matches = try {
            jwkSource.get(selector, null)
        } catch (e: RateLimitReachedException) {
            log.warn("Apple JWKS refresh rate-limited for unknown kid={} reason={}", kid, e.message)
            return null
        } catch (e: KeySourceException) {
            throw IllegalStateException("Failed to fetch Apple JWKs", e)
        }
        return matches.firstOrNull() as? RSAKey
    }

    companion object {
        val OUTAGE_TOLERANCE: Duration = Duration.ofHours(24)
        val RATE_LIMIT_INTERVAL: Duration = Duration.ofSeconds(30)
        private val CACHE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(15)
        private val HTTP_TIMEOUT: Duration = Duration.ofSeconds(3)
        private const val HTTP_SIZE_LIMIT_BYTES = 50 * 1024

        fun create(props: AppleOidcProperties): AppleJwksCache {
            val retriever = DefaultResourceRetriever(
                HTTP_TIMEOUT.toMillis().toInt(),
                HTTP_TIMEOUT.toMillis().toInt(),
                HTTP_SIZE_LIMIT_BYTES,
            )
            val origin = URLBasedJWKSetSource<SecurityContext>(URI(props.jwksUri).toURL(), retriever)
            return create(origin, Duration.ofMinutes(props.jwkCacheTtlMinutes))
        }

        // Origin and durations are injectable so tests can simulate outages without real time or network.
        fun create(
            origin: JWKSetSource<SecurityContext>,
            cacheTtl: Duration,
            outageTolerance: Duration = OUTAGE_TOLERANCE,
            rateLimitInterval: Duration = RATE_LIMIT_INTERVAL,
        ): AppleJwksCache {
            val source = JWKSourceBuilder.create(origin)
                .cache(cacheTtl.toMillis(), CACHE_REFRESH_TIMEOUT.toMillis())
                .refreshAheadCache(false)
                .rateLimited(rateLimitInterval.toMillis())
                .outageTolerant(outageTolerance.toMillis())
                .build()
            return AppleJwksCache(source)
        }
    }
}
