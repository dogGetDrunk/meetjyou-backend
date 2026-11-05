package com.dogGetDrunk.meetjyou.auth.social.apple

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
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
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * AT-004-1: AppleJwtDecoder verifies the signature before any claim and rejects missing claims with 401.
 * Real decoder + in-memory JWKS origin + generated RSA keys, no network.
 */
class AppleInvalidClaimsDecoderTest : BehaviorSpec() {

    init {
        missingClaimCases()
        signatureFirstCases()
        preservedBehaviorCases()
        claimMismatchCases()
        jwksFailureCases()
    }

    private fun missingClaimCases() {
        given("AT-004-1 valid signature but a required claim is missing") {
            val decoder = decoder()

            `when`("exp is missing") {
                then("InvalidJwtException (not NPE) with a WARN naming only the claim") {
                    val token = token(exp = null)
                    assertMissingClaimRejected(decoder, token, "exp")
                }
            }
            `when`("iat is missing") {
                then("InvalidJwtException (not NPE) with a WARN naming only the claim") {
                    val token = token(iat = null)
                    assertMissingClaimRejected(decoder, token, "iat")
                }
            }
            `when`("sub is missing") {
                then("InvalidJwtException (not NPE) with a WARN naming only the claim") {
                    val token = token(sub = null)
                    assertMissingClaimRejected(decoder, token, "sub")
                }
            }
            `when`("sub is blank") {
                then("InvalidJwtException (not NPE) with a WARN naming only the claim") {
                    val token = token(sub = BLANK_SUBJECT)
                    assertMissingClaimRejected(decoder, token, "sub")
                }
            }
        }
    }

    private fun signatureFirstCases() {
        given("AT-004-1 token signed by a key the JWKS does not trust under a trusted kid") {
            val decoder = decoder()

            `when`("the exp is already in the past") {
                then("rejected as a signature failure, not as an expired token") {
                    val token = token(signer = UNTRUSTED_KEY, exp = PAST)
                    val logs = captureWarnings {
                        val e = shouldThrow<InvalidJwtException> { decoder.decode(token) }
                        e.message shouldNotContain EXPIRED_MESSAGE
                    }
                    logs.none { it.contains("expired", ignoreCase = true) } shouldBe true
                }
            }
            `when`("the exp is missing") {
                then("InvalidJwtException as a signature failure") {
                    val token = token(signer = UNTRUSTED_KEY, exp = null)
                    val e = shouldThrow<InvalidJwtException> { decoder.decode(token) }
                    e.message shouldNotContain EXPIRED_MESSAGE
                }
            }
        }
    }

    private fun preservedBehaviorCases() {
        given("AT-004-1 existing behavior that must keep working") {
            val decoder = decoder()

            `when`("a normal token is decoded") {
                then("subject, issuedAt and expiresAt are the token's values") {
                    val issuedAt = Instant.ofEpochSecond(Instant.now().epochSecond)
                    val expiresAt = issuedAt.plusSeconds(LIFETIME_SECONDS)
                    val jwt = decoder.decode(token(iat = issuedAt, exp = expiresAt))
                    jwt.subject shouldBe SUBJECT
                    jwt.issuedAt shouldBe issuedAt
                    jwt.expiresAt shouldBe expiresAt
                }
            }
            `when`("the signature is valid but the token expired") {
                then("InvalidJwtException with the expired message") {
                    val e = shouldThrow<InvalidJwtException> { decoder.decode(token(exp = PAST)) }
                    e.message shouldBe EXPIRED_MESSAGE
                }
            }
        }
    }

    private fun claimMismatchCases() {
        given("AT-004-1 existing issuer and audience checks") {
            val decoder = decoder()

            `when`("the issuer does not match") {
                then("InvalidJwtException") {
                    shouldThrow<InvalidJwtException> { decoder.decode(token(issuer = "https://evil.example")) }
                }
            }
            `when`("the audience does not match") {
                then("InvalidJwtException") {
                    shouldThrow<InvalidJwtException> { decoder.decode(token(audience = "other-client")) }
                }
            }
        }
    }

    private fun jwksFailureCases() {
        given("AT-004-1 the JWKS key supply fails") {
            `when`("a well-formed token is decoded") {
                then("ExternalServiceException propagates as-is, not InvalidJwtException") {
                    val origin = FakeOrigin(JWKSet(TRUSTED_KEY.toPublicJWK())).apply { failing = true }
                    val decoder = AppleJwtDecoder(PROPS, AppleJwksCache.create(origin, LONG_TTL))
                    val e = shouldThrow<ExternalServiceException> { decoder.decode(token()) }
                    e.errorCode shouldBe ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE
                }
            }
        }
    }

    private fun assertMissingClaimRejected(decoder: AppleJwtDecoder, token: String, claim: String) {
        val logs = captureWarnings {
            shouldThrow<InvalidJwtException> { decoder.decode(token) }
        }
        val missingLog = logs.firstOrNull { it.contains(claim) }
        missingLog shouldNotBe null
        logs.forEach { line ->
            line shouldNotContain token
            line shouldNotContain token.substringAfterLast('.')
        }
    }

    private fun captureWarnings(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(AppleJwtDecoder::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.filter { it.level == ch.qos.logback.classic.Level.WARN }.map { it.formattedMessage }
    }

    private fun decoder(): AppleJwtDecoder {
        val origin = FakeOrigin(JWKSet(TRUSTED_KEY.toPublicJWK()))
        return AppleJwtDecoder(PROPS, AppleJwksCache.create(origin, LONG_TTL))
    }

    private class FakeOrigin(var keySet: JWKSet) : JWKSetSource<SecurityContext> {
        var failing = false

        override fun getJWKSet(
            refreshEvaluator: JWKSetCacheRefreshEvaluator,
            currentTime: Long,
            context: SecurityContext?,
        ): JWKSet {
            if (failing) throw JWKSetRetrievalException("Apple JWKS unavailable", IOException("connection refused"))
            return keySet
        }

        override fun close() = Unit
    }

    private companion object {
        const val TRUSTED_KID = "apple-trusted-key"
        const val RSA_KEY_SIZE = 2048
        const val SUBJECT = "apple-sub-123"
        const val BLANK_SUBJECT = "   "
        const val EXPIRED_MESSAGE = "Token has expired"
        const val LIFETIME_SECONDS = 600L
        const val PAST_SECONDS = 3600L

        val LONG_TTL: Duration = Duration.ofMinutes(10)
        val PAST: Instant = Instant.now().minusSeconds(PAST_SECONDS)

        val TRUSTED_KEY: RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID(TRUSTED_KID).generate()
        val UNTRUSTED_KEY: RSAKey = RSAKeyGenerator(RSA_KEY_SIZE).keyID("untrusted").generate()

        val PROPS = AppleOidcProperties(
            issuer = "https://appleid.apple.com",
            jwksUri = "https://appleid.apple.com/auth/keys",
            clientId = "test-apple-client",
            clockSkewSeconds = 60,
            jwkCacheTtlMinutes = 10,
        )

        // Null claim parameters are left out of the token; the header kid is always the trusted one.
        @Suppress("LongParameterList")
        fun token(
            signer: RSAKey = TRUSTED_KEY,
            issuer: String = PROPS.issuer,
            audience: String = PROPS.clientId,
            sub: String? = SUBJECT,
            iat: Instant? = Instant.now(),
            exp: Instant? = Instant.now().plusSeconds(LIFETIME_SECONDS),
        ): String {
            val claims = JWTClaimsSet.Builder().issuer(issuer).audience(audience)
            sub?.let { claims.subject(it) }
            iat?.let { claims.issueTime(Date.from(it)) }
            exp?.let { claims.expirationTime(Date.from(it)) }
            val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TRUSTED_KID).build()
            val jwt = SignedJWT(header, claims.build())
            jwt.sign(RSASSASigner(signer))
            return jwt.serialize()
        }
    }
}
