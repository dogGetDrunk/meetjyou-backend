package com.dogGetDrunk.meetjyou.auth.social.apple

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class AppleJwksCache(
    private val props: AppleOidcProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val rest = RestClient.builder()
        .baseUrl(props.jwksUri)
        .build()

    private val cache: Cache<String, RSAKey> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(props.jwkCacheTtlMinutes))
        .maximumSize(32)
        .build()

    fun getRsaKeyByKid(kid: String): RSAKey? {
        cache.getIfPresent(kid)?.let { return it }

        refreshAllKeys()

        return cache.getIfPresent(kid)
    }

    private fun refreshAllKeys() {
        val response = rest.get()
            .retrieve()
            .onStatus({ s -> s.is4xxClientError || s.is5xxServerError }) { _, response ->
                log.error("Apple JWKs 조회 실패: HTTP ${response.statusCode}")
                throw IllegalStateException("Failed to fetch Apple JWKs")
            }
            .toEntity(String::class.java)

        check(response.statusCode == HttpStatus.OK && !response.body.isNullOrBlank()) {
            "Failed to fetch Apple JWKs: empty body"
        }

        val jwkSet = JWKSet.parse(response.body)
        var loaded = 0
        for (jwk in jwkSet.keys) {
            val rsa = jwk as? RSAKey
            val kid = rsa?.keyID
            if (rsa != null && kid != null) {
                cache.put(kid, rsa)
                loaded++
            }
        }

        log.debug("Refreshed Apple JWKS. loadedKeys={}", loaded)
    }
}
