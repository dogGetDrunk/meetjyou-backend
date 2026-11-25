package com.dogGetDrunk.meetjyou.auth.social.kakao

import com.dogGetDrunk.meetjyou.auth.social.AccessToken
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.ExternalServiceException
import com.dogGetDrunk.meetjyou.config.KakaoOidcConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * AT-003 (unit): the Kakao REST client built by KakaoOidcConfig applies its read timeout, and a
 * timeout maps to SOCIAL_PROVIDER_UNAVAILABLE.
 */
class KakaoRestClientTimeoutTest : BehaviorSpec({

    fun props(baseUrl: String, readTimeout: Duration = Duration.ofMillis(READ_TIMEOUT_MS)) = KakaoOidcProperties(
        issuer = "https://kauth.kakao.com",
        jwksUri = "$baseUrl/oauth/keys",
        accessTokenInfoUri = "$baseUrl/v1/user/access_token_info",
        userInfoUri = "$baseUrl/v2/user/me",
        clientId = "test-client-id",
        appId = "12345678",
        clockSkewSeconds = 60,
        jwkCacheTtlMinutes = 60,
        readTimeout = readTimeout,
    )

    given("Kakao timeout 기본값") {
        then("connect 3s, read 5s") {
            val defaults = props("http://127.0.0.1:1", KakaoOidcProperties.DEFAULT_READ_TIMEOUT)
            defaults.connectTimeout shouldBe Duration.ofSeconds(3)
            defaults.readTimeout shouldBe Duration.ofSeconds(5)
        }
    }

    given("Kakao가 요청을 받고 응답을 보류하면") {
        then("read timeout 뒤 SOCIAL_PROVIDER_UNAVAILABLE로 1초 안에 실패한다") {
            HeldResponseServer().use { server ->
                val config = props(server.baseUrl)
                val client = KakaoOidcConfig(config).kakaoRestClient(RestClient.builder())
                val sut = KakaoVerifier(config, mockk(), SimpleMeterRegistry(), client)

                val startedNs = System.nanoTime()
                val error = shouldThrow<ExternalServiceException> {
                    sut.verifyAndExtract(AccessToken("held-token"), null)
                }
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs)

                server.received.count shouldBe 0L
                error.errorCode shouldBe ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE
                elapsedMs shouldBeLessThan MAX_ELAPSED_MS
            }
        }
    }
}) {
    private companion object {
        const val READ_TIMEOUT_MS = 200L
        const val MAX_ELAPSED_MS = 1_000L
    }
}
