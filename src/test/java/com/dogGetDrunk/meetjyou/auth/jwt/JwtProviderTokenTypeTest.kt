package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.Date
import java.util.UUID

class JwtProviderTokenTypeTest : BehaviorSpec({
    val secret = Encoders.BASE64.encode(ByteArray(KEY_BYTES) { it.toByte() })
    val sut = JwtProvider(secret, ACCESS_TTL_MILLIS, REFRESH_TTL_MILLIS, ISSUER)
    val userUuid = UUID.randomUUID()

    // Tokens minted the way JwtProvider did before the token_type claim existed.
    fun legacyToken(jti: String?): String {
        val now = Date()
        val builder = Jwts.builder()
            .issuer(ISSUER)
            .subject(EMAIL)
            .claim("userUuid", userUuid.toString())
            .issuedAt(now)
            .expiration(Date(now.time + ACCESS_TTL_MILLIS))
        jti?.let { builder.id(it) }
        return builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), Jwts.SIG.HS256).compact()
    }

    Given("새로 발급된 토큰") {
        val access = sut.generateAccessToken(userUuid, EMAIL, Role.USER)
        val refresh = sut.generateRefreshToken(userUuid, EMAIL).token

        When("access 전용 검증을 하면") {
            Then("access token은 통과하고 refresh token은 거부된다") {
                shouldNotThrowAny { sut.validateAccessTokenOrThrow(access) }
                shouldThrow<InvalidJwtException> { sut.validateAccessTokenOrThrow(refresh) }
            }
        }

        When("refresh 여부를 판정하면") {
            Then("refresh token만 true다") {
                sut.isRefreshToken(refresh) shouldBe true
                sut.isRefreshToken(access) shouldBe false
            }
        }
    }

    Given("token_type claim이 없는 배포 전 발급 토큰") {
        When("jti가 없으면(구 access token)") {
            Then("만료 전까지 access token으로 통과한다") {
                val legacyAccess = legacyToken(jti = null)

                shouldNotThrowAny { sut.validateAccessTokenOrThrow(legacyAccess) }
                sut.isRefreshToken(legacyAccess) shouldBe false
            }
        }

        When("jti가 있으면(구 refresh token)") {
            Then("access 자리에서는 거부되고 refresh로만 인정된다") {
                val legacyRefresh = legacyToken(jti = UUID.randomUUID().toString())

                shouldThrow<InvalidJwtException> { sut.validateAccessTokenOrThrow(legacyRefresh) }
                sut.isRefreshToken(legacyRefresh) shouldBe true
            }
        }
    }
})

private const val KEY_BYTES = 32
private const val ACCESS_TTL_MILLIS = 60_000L
private const val REFRESH_TTL_MILLIS = 600_000L
private const val ISSUER = "test-issuer"
private const val EMAIL = "user@example.com"
