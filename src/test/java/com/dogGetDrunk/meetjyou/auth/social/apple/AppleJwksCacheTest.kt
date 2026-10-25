package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.ExternalServiceException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator
import com.nimbusds.jose.jwk.source.JWKSetRetrievalException
import com.nimbusds.jose.jwk.source.JWKSetSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Date

class AppleJwksCacheTest : BehaviorSpec() {

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        given("키 셋을 한 번 받아 둔 뒤 TTL이 지났을 때") {
            `when`("Apple JWKS 갱신이 실패하면") {
                then("이전에 받은 키로 계속 검증한다") {
                    val origin = FakeOrigin(JWKSet(KEY_1.toPublicJWK()))
                    val cache = AppleJwksCache.create(origin, SHORT_TTL, rateLimitInterval = NO_RATE_LIMIT)
                    cache.getRsaKeyByKid(KID_1) shouldNotBe null

                    Thread.sleep(PAST_TTL_MILLIS)
                    origin.failing = true

                    cache.getRsaKeyByKid(KID_1)?.keyID shouldBe KID_1
                    origin.calls shouldBe 2
                }
            }

            `when`("장애가 허용 기간보다 길어지면") {
                then("서버 오류로 실패한다") {
                    val origin = FakeOrigin(JWKSet(KEY_1.toPublicJWK()))
                    val cache = AppleJwksCache.create(
                        origin,
                        SHORT_TTL,
                        outageTolerance = SHORT_OUTAGE_TOLERANCE,
                        rateLimitInterval = NO_RATE_LIMIT,
                    )
                    cache.getRsaKeyByKid(KID_1) shouldNotBe null
                    origin.failing = true

                    Thread.sleep(PAST_OUTAGE_TOLERANCE_MILLIS)

                    shouldThrow<ExternalServiceException> { cache.getRsaKeyByKid(KID_1) }.errorCode shouldBe
                        ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE
                }
            }
        }

        given("키 셋을 한 번도 받지 못한 상태에서") {
            `when`("Apple JWKS 조회가 실패하면") {
                then("토큰 오류로 위장하지 않고 공급자 장애(503)로 실패한다") {
                    val origin = FakeOrigin(JWKSet(KEY_1.toPublicJWK())).apply { failing = true }
                    val cache = AppleJwksCache.create(origin, LONG_TTL)

                    shouldThrow<ExternalServiceException> { cache.getRsaKeyByKid(KID_1) }.errorCode shouldBe
                        ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE
                }
            }
        }

        given("캐시에 없는 kid가 반복해서 오면") {
            `when`("쿨다운 안에 여러 번 조회하면") {
                then("Apple 재조회 횟수가 제한되고 null을 반환한다") {
                    val origin = FakeOrigin(JWKSet(KEY_1.toPublicJWK()))
                    val cache = AppleJwksCache.create(origin, LONG_TTL)
                    cache.getRsaKeyByKid(KID_1) shouldNotBe null

                    repeat(FORGED_KID_ATTEMPTS) { cache.getRsaKeyByKid("forged-$it") shouldBe null }

                    origin.calls shouldBe MAX_FETCHES_PER_RATE_LIMIT_WINDOW
                }
            }
        }

        given("Apple이 키를 교체했을 때") {
            `when`("새 kid로 서명된 토큰이 오면") {
                then("재조회해서 새 키를 찾는다") {
                    val origin = FakeOrigin(JWKSet(KEY_1.toPublicJWK()))
                    val cache = AppleJwksCache.create(origin, LONG_TTL)
                    cache.getRsaKeyByKid(KID_1) shouldNotBe null

                    origin.keySet = JWKSet(listOf(KEY_1.toPublicJWK(), KEY_2.toPublicJWK()))

                    cache.getRsaKeyByKid(KID_2)?.keyID shouldBe KID_2
                }
            }
        }

        given("AppleJwtDecoder와 함께 쓸 때") {
            val origin = FakeOrigin(JWKSet(KEY_1.toPublicJWK()))
            val cache = AppleJwksCache.create(origin, SHORT_TTL, rateLimitInterval = NO_RATE_LIMIT)
            val decoder = AppleJwtDecoder(PROPS, cache)

            `when`("TTL이 지난 뒤 Apple 장애 중에 로그인하면") {
                then("유효한 id_token은 그대로 검증된다") {
                    decoder.decode(signedToken(KEY_1)).subject shouldBe SUBJECT

                    Thread.sleep(PAST_TTL_MILLIS)
                    origin.failing = true

                    decoder.decode(signedToken(KEY_1)).subject shouldBe SUBJECT
                }
            }

            `when`("알 수 없는 kid로 서명된 토큰이면") {
                then("401(InvalidJwtException)로 거부한다") {
                    shouldThrow<InvalidJwtException> { decoder.decode(signedToken(KEY_2)) }
                }
            }
        }
    }

    private class FakeOrigin(var keySet: JWKSet) : JWKSetSource<SecurityContext> {
        var failing = false
        var calls = 0

        override fun getJWKSet(
            refreshEvaluator: JWKSetCacheRefreshEvaluator,
            currentTime: Long,
            context: SecurityContext?,
        ): JWKSet {
            calls++
            if (failing) throw JWKSetRetrievalException("Apple JWKS unavailable", IOException("connection refused"))
            return keySet
        }

        override fun close() = Unit
    }

    private companion object {
        const val KID_1 = "apple-key-1"
        const val KID_2 = "apple-key-2"
        const val SUBJECT = "apple-sub-123"
        const val RSA_KEY_SIZE = 2048
        const val FORGED_KID_ATTEMPTS = 20
        const val TOKEN_LIFETIME_SECONDS = 600L

        // Nimbus rate limiter allows two fetches per window: the initial load plus one kid-miss refresh.
        const val MAX_FETCHES_PER_RATE_LIMIT_WINDOW = 2

        val SHORT_TTL: Duration = Duration.ofMillis(100)
        val LONG_TTL: Duration = Duration.ofMinutes(10)
        val SHORT_OUTAGE_TOLERANCE: Duration = Duration.ofMillis(300)
        val NO_RATE_LIMIT: Duration = Duration.ofMillis(1)
        const val PAST_TTL_MILLIS = 200L
        const val PAST_OUTAGE_TOLERANCE_MILLIS = 450L

        val KEY_1: RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID(KID_1).generate()
        val KEY_2: RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID(KID_2).generate()

        val PROPS = AppleOidcProperties(
            issuer = "https://appleid.apple.com",
            jwksUri = "https://appleid.apple.com/auth/keys",
            clientId = "test-apple-client",
            clockSkewSeconds = 60,
            jwkCacheTtlMinutes = 10,
        )

        fun signedToken(key: RSAKey): String {
            val now = Instant.now()
            val claims = JWTClaimsSet.Builder()
                .issuer(PROPS.issuer)
                .audience(PROPS.clientId)
                .subject(SUBJECT)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(TOKEN_LIFETIME_SECONDS)))
                .build()
            val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            jwt.sign(RSASSASigner(key))
            return jwt.serialize()
        }
    }
}
