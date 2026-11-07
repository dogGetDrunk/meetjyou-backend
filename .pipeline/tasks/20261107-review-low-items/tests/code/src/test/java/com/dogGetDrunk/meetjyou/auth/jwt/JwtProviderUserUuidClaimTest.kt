package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.security.Keys
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.Date
import java.util.UUID

// AT-002-1: getUserUuid maps malformed/missing userUuid claims to InvalidJwtException; getExpiration is gone.
class JwtProviderUserUuidClaimTest : BehaviorSpec({
    val secret = Encoders.BASE64.encode(ByteArray(KEY_BYTES) { it.toByte() })
    val sut = JwtProvider(secret, ACCESS_TTL_MILLIS, REFRESH_TTL_MILLIS, ISSUER)

    // Signs an access-type token with the same key, optionally with a custom userUuid claim.
    fun signedToken(userUuidClaim: String?): String {
        val now = Date()
        val builder = Jwts.builder()
            .issuer(ISSUER)
            .subject(SUBJECT)
            .claim("token_type", "access")
            .issuedAt(now)
            .expiration(Date(now.time + ACCESS_TTL_MILLIS))
        userUuidClaim?.let { builder.claim("userUuid", it) }
        return builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), Jwts.SIG.HS256).compact()
    }

    Given("AT-002-1 userUuid 클레임이 UUID 형식이 아닌 서명된 토큰") {
        val token = signedToken("not-a-uuid")

        When("getUserUuid를 호출하면") {
            Then("InvalidJwtException(INVALID_JWT)이 발생하고 IllegalArgumentException이 아니다") {
                val thrown = shouldThrow<InvalidJwtException> { sut.getUserUuid(token) }
                thrown.errorCode shouldBe ErrorCode.INVALID_JWT
                (thrown is IllegalArgumentException) shouldBe false
            }
        }
    }

    Given("AT-002-1 userUuid 클레임이 없는 서명된 토큰") {
        val token = signedToken(null)

        When("getUserUuid를 호출하면") {
            Then("InvalidJwtException(INVALID_JWT)이 발생한다") {
                val thrown = shouldThrow<InvalidJwtException> { sut.getUserUuid(token) }
                thrown.errorCode shouldBe ErrorCode.INVALID_JWT
            }
        }
    }

    Given("AT-002-1 generateAccessToken으로 발급한 정상 토큰") {
        val userUuid = UUID.randomUUID()
        val token = sut.generateAccessToken(userUuid, Role.USER)

        When("getUserUuid를 호출하면") {
            Then("발급에 쓴 UUID가 그대로 나온다") {
                sut.getUserUuid(token) shouldBe userUuid
            }
        }
    }

    Given("AT-002-1 JwtProvider 클래스") {
        When("메서드를 리플렉션으로 조회하면") {
            Then("getExpiration이라는 이름의 메서드가 없다") {
                val names = JwtProvider::class.java.methods.map { it.name } +
                    JwtProvider::class.java.declaredMethods.map { it.name }
                names.contains("getExpiration") shouldBe false
            }
        }
    }
})

private const val KEY_BYTES = 32
private const val ACCESS_TTL_MILLIS = 60_000L
private const val REFRESH_TTL_MILLIS = 600_000L
private const val ISSUER = "test-issuer"
private const val SUBJECT = "subject"
