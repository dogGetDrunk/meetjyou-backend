package com.dogGetDrunk.meetjyou.config

import com.dogGetDrunk.meetjyou.config.property.LoadTestTokenProperties
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec

class LoadTestTokenKillSwitchTest : BehaviorSpec({

    given("load-test-token.enabled=true") {
        `when`("secret가 32자 미만이면") {
            then("애플리케이션 기동이 차단된다") {
                val props = LoadTestTokenProperties(enabled = true, secret = "too-short")

                shouldThrow<IllegalStateException> {
                    LoadTestTokenKillSwitch(props).guard()
                }
            }
        }

        `when`("secret가 32자 이상이면") {
            then("정상적으로 기동된다") {
                val props = LoadTestTokenProperties(enabled = true, secret = "a".repeat(32))

                shouldNotThrowAny {
                    LoadTestTokenKillSwitch(props).guard()
                }
            }
        }
    }

    given("load-test-token.enabled=false") {
        `when`("secret가 비어있어도") {
            then("정상적으로 기동된다") {
                val props = LoadTestTokenProperties(enabled = false, secret = "")

                shouldNotThrowAny {
                    LoadTestTokenKillSwitch(props).guard()
                }
            }
        }
    }
})
