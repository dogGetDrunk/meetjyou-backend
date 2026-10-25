package com.dogGetDrunk.meetjyou.auth.social.apple

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.security.interfaces.ECPublicKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AppleClientSecretFactoryTest : BehaviorSpec({

    val now = Instant.parse("2026-10-25T00:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)
    val publicKey = AppleTestKeys.keyPair.public as ECPublicKey

    given("client_secret 생성 시") {
        `when`(".p8 파일 그대로(PEM 헤더·줄바꿈 포함) 설정하면") {
            then("팀 키로 서명한 ES256 JWT를 애플 규격 claim으로 만든다") {
                val secret = AppleClientSecretFactory(AppleTestKeys.props(), clock).create()

                val jwt = SignedJWT.parse(secret)
                jwt.verify(ECDSAVerifier(publicKey)) shouldBe true
                jwt.header.algorithm shouldBe JWSAlgorithm.ES256
                jwt.header.keyID shouldBe AppleTestKeys.KEY_ID
                jwt.jwtClaimsSet.issuer shouldBe AppleTestKeys.TEAM_ID
                jwt.jwtClaimsSet.subject shouldBe AppleTestKeys.CLIENT_ID
                jwt.jwtClaimsSet.audience shouldContainExactly listOf(AppleClientSecretFactory.APPLE_AUDIENCE)
                jwt.jwtClaimsSet.issueTime.toInstant() shouldBe now
                jwt.jwtClaimsSet.expirationTime.toInstant() shouldBe now.plus(AppleClientSecretFactory.SECRET_TTL)
            }
        }

        `when`("배포 .env처럼 base64 본문 한 줄만 설정하면") {
            then("같은 키로 서명한다") {
                val props = AppleTestKeys.props(privateKey = AppleTestKeys.singleLineBody)

                val jwt = SignedJWT.parse(AppleClientSecretFactory(props, clock).create())

                jwt.verify(ECDSAVerifier(publicKey)) shouldBe true
            }
        }

        `when`("키가 깨져 있으면") {
            then("첫 탈퇴가 아니라 생성(기동) 시점에 실패한다") {
                shouldThrow<IllegalArgumentException> {
                    AppleClientSecretFactory(AppleTestKeys.props(privateKey = "not-a-key!"), clock)
                }
            }
        }

        `when`("연동 해제가 꺼져 있으면") {
            then("생성은 되지만 create 호출은 거부한다") {
                val factory = AppleClientSecretFactory(AppleTestKeys.disabledProps(), clock)

                shouldThrow<IllegalStateException> { factory.create() }
            }
        }
    }
})
