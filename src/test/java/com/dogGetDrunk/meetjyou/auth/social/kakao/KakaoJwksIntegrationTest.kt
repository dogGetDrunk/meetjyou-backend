package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.dogGetDrunk.meetjyou.config.KakaoOidcConfig
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtException
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class KakaoJwksIntegrationTest : FunSpec({
    test("unknown kids cannot amplify JWKS calls and cached valid tokens still work") {
        JwksServer().use { server ->
            val decoder = KakaoOidcConfig(server.props()).kakaoJwtDecoder()
            decoder.decode(token(KEY)).subject shouldBe SUBJECT
            repeat(ATTEMPTS) {
                shouldThrow<BadJwtException> { decoder.decode(token(OTHER_KEY)) }
            }
            server.calls.get() shouldBeLessThanOrEqual MAX_CALLS
            decoder.decode(token(KEY)).subject shouldBe SUBJECT
        }
    }

    test("key rotation fetches the new signing key") {
        JwksServer().use { server ->
            val decoder = KakaoOidcConfig(server.props()).kakaoJwtDecoder()
            decoder.decode(token(KEY)).subject shouldBe SUBJECT
            server.body = JWKSet(listOf(KEY.toPublicJWK(), OTHER_KEY.toPublicJWK())).toString()
            decoder.decode(token(OTHER_KEY)).subject shouldBe SUBJECT
        }
    }

    test("unavailable initial JWKS is a provider error") {
        JwksServer().use { server ->
            server.status = UNAVAILABLE
            val decoder = KakaoOidcConfig(server.props()).kakaoJwtDecoder()
            val error = shouldThrow<JwtException> { decoder.decode(token(KEY)) }
            (error is BadJwtException) shouldBe false
        }
    }

    test("signature issuer audience expiry and algorithm validation remain enforced") {
        JwksServer().use { server ->
            val decoder = KakaoOidcConfig(server.props()).kakaoJwtDecoder()
            decoder.decode(token(KEY)).subject shouldBe SUBJECT
            shouldThrow<BadJwtException> { decoder.decode(token(RSAKey.Builder(OTHER_KEY).keyID(KEY.keyID).build())) }
            shouldThrow<BadJwtException> { decoder.decode(token(KEY, issuer = "wrong")) }
            shouldThrow<BadJwtException> { decoder.decode(token(KEY, audience = "wrong")) }
            shouldThrow<BadJwtException> { decoder.decode(token(KEY, lifetime = -EXPIRED_SECONDS)) }
            shouldThrow<BadJwtException> { decoder.decode(token(KEY, algorithm = JWSAlgorithm.RS512)) }
        }
    }
}) {
    private class JwksServer : AutoCloseable {
        val calls = AtomicInteger()
        var body = JWKSet(KEY.toPublicJWK()).toString()
        var status = OK
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/keys") { exchange ->
                calls.incrementAndGet()
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

        fun props() = KakaoOidcProperties(
            ISSUER, "http://127.0.0.1:${server.address.port}/keys", "", "", AUDIENCE, "", 60, 10,
        )

        override fun close() = server.stop(0)
    }

    private companion object {
        const val SUBJECT = "kakao-user"
        const val ISSUER = "https://kauth.kakao.com"
        const val AUDIENCE = "test-client"
        const val ATTEMPTS = 10
        const val MAX_CALLS = 2
        const val OK = 200
        const val UNAVAILABLE = 503
        const val EXPIRED_SECONDS = 120L
        const val LIFETIME_SECONDS = 600L
        const val KEY_SIZE = 2048
        val KEY: RSAKey = RSAKeyGenerator(KEY_SIZE).keyID("key-1").generate()
        val OTHER_KEY: RSAKey = RSAKeyGenerator(KEY_SIZE).keyID("key-2").generate()

        fun token(
            key: RSAKey,
            issuer: String = ISSUER,
            audience: String = AUDIENCE,
            lifetime: Long = LIFETIME_SECONDS,
            algorithm: JWSAlgorithm = JWSAlgorithm.RS256,
        ): String {
            val claims = JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(SUBJECT)
                .expirationTime(Date.from(Instant.now().plusSeconds(lifetime))).build()
            val jwt = SignedJWT(JWSHeader.Builder(algorithm).keyID(key.keyID).build(), claims)
            jwt.sign(RSASSASigner(key))
            return jwt.serialize()
        }
    }
}
