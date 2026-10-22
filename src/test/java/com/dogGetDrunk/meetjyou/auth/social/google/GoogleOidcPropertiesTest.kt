package com.dogGetDrunk.meetjyou.auth.social.google

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class GoogleOidcPropertiesTest : BehaviorSpec({

    given("GoogleOidcProperties 생성 시") {
        `when`("모든 클라이언트 ID가 채워져 있으면") {
            then("clientIds로 전부 노출한다") {
                val props = GoogleOidcProperties(ISSUER, mapOf("android" to "a-id", "web" to "w-id", "ios" to "i-id"))

                props.clientIds shouldContainExactlyInAnyOrder listOf("a-id", "w-id", "i-id")
            }
        }

        `when`("값이 해석되지 않은 placeholder면") {
            then("어떤 키인지 담아 예외를 던진다") {
                val ex = shouldThrow<IllegalStateException> {
                    GoogleOidcProperties(ISSUER, mapOf("web" to "w-id", "ios" to UNRESOLVED_IOS))
                }

                ex.message shouldContain "ios"
            }
        }

        `when`("값이 비어 있으면") {
            then("예외를 던진다") {
                shouldThrow<IllegalStateException> {
                    GoogleOidcProperties(ISSUER, mapOf("web" to "w-id", "ios" to " "))
                }
            }
        }

        `when`("클라이언트가 하나도 없으면") {
            then("예외를 던진다") {
                shouldThrow<IllegalStateException> { GoogleOidcProperties(ISSUER, emptyMap()) }
            }
        }
    }

    given("Spring 바인딩으로 기동할 때") {
        val runner = ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig::class.java)
            .withPropertyValues(
                "auth.google.issuer=$ISSUER",
                "auth.google.clients.android=a-id",
                "auth.google.clients.web=w-id",
            )

        `when`("ios 값의 env가 정의되지 않았으면") {
            then("컨텍스트 기동이 실패한다") {
                runner.withPropertyValues("auth.google.clients.ios=$UNRESOLVED_IOS").run { context ->
                    context.startupFailure.shouldNotBeNull()
                }
            }
        }

        `when`("ios 값이 채워져 있으면") {
            then("정상 기동한다") {
                runner.withPropertyValues("auth.google.clients.ios=i-id").run { context ->
                    context.startupFailure.shouldBeNull()
                }
            }
        }
    }
}) {
    @Configuration
    @EnableConfigurationProperties(GoogleOidcProperties::class)
    class PropertiesConfig

    companion object {
        private const val ISSUER = "https://accounts.google.com"
        private const val UNRESOLVED_IOS = "\${GOOGLE_CLIENT_ID_IOS_NOT_SET}"
    }
}
