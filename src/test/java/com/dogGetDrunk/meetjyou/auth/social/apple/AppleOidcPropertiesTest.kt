package com.dogGetDrunk.meetjyou.auth.social.apple

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AppleOidcPropertiesTest : BehaviorSpec({

    given("AppleOidcProperties의 연동 해제 자격 증명") {
        `when`("team-id·key-id·private-key가 모두 비어 있으면 (키 발급 전 배포)") {
            then("앱은 정상 기동하고 연동 해제만 꺼진다") {
                AppleTestKeys.disabledProps().revocationEnabled shouldBe false
            }
        }

        `when`("셋 다 채워져 있으면") {
            then("연동 해제가 켜진다") {
                AppleTestKeys.props().revocationEnabled shouldBe true
            }
        }

        `when`("일부만 채워져 있으면 (설정 누락)") {
            then("기동 시점에 예외로 드러낸다") {
                shouldThrow<IllegalStateException> {
                    AppleTestKeys.props().copy(keyId = "")
                }
            }
        }

        `when`("env가 없어 placeholder가 해석되지 않은 채 남으면") {
            then("비어 있는 것으로 보고 일부 누락이면 예외를 던진다") {
                shouldThrow<IllegalStateException> {
                    AppleTestKeys.props().copy(privateKey = "\${APPLE_PRIVATE_KEY}")
                }
            }
        }
    }
})
