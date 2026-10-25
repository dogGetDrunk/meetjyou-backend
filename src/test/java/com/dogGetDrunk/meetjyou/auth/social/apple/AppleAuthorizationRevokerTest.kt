package com.dogGetDrunk.meetjyou.auth.social.apple

import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthorizationRevoker.Outcome
import com.dogGetDrunk.meetjyou.auth.social.apple.AppleAuthorizationRevoker.Trigger
import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder

class AppleAuthorizationRevokerTest : BehaviorSpec() {

    private lateinit var appleAuthClient: AppleAuthClient
    private lateinit var appleJwtDecoder: JwtDecoder
    private lateinit var discordAlertService: DiscordAlertService
    private lateinit var meterRegistry: SimpleMeterRegistry

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun revoker(props: AppleOidcProperties = AppleTestKeys.props()) =
        AppleAuthorizationRevoker(props, appleAuthClient, appleJwtDecoder, meterRegistry, discordAlertService)

    private fun count(outcome: Outcome, trigger: Trigger = Trigger.WITHDRAWAL): Double =
        meterRegistry.find(AppleAuthorizationRevoker.METRIC_NAME)
            .tags("outcome", outcome.tag, "trigger", trigger.tag)
            .counter()?.count() ?: 0.0

    private fun stubExchange(subject: String, refreshToken: String? = REFRESH_TOKEN) {
        every { appleAuthClient.exchangeCode(CODE) } returns
            AppleTokenResponse(accessToken = ACCESS_TOKEN, refreshToken = refreshToken, idToken = ID_TOKEN)
        every { appleJwtDecoder.decode(ID_TOKEN) } returns
            Jwt.withTokenValue(ID_TOKEN).header("alg", "RS256").subject(subject).build()
    }

    init {
        // Stateful mocks and counters are rebuilt per leaf: one spec instance can run several leaves.
        beforeEach {
            appleAuthClient = mockk(relaxed = true)
            appleJwtDecoder = mockk()
            discordAlertService = mockk(relaxed = true)
            meterRegistry = SimpleMeterRegistry()
        }

        given("애플 계정이 연결된 회원의 연동을 해제할 때") {
            `when`("code의 sub가 회원의 애플 계정과 같으면") {
                then("code를 교환하고 refresh_token을 해제한다") {
                    stubExchange(subject = APPLE_SUB)

                    revoker().revokeQuietly(CODE, APPLE_SUB, Trigger.WITHDRAWAL)

                    verify { appleAuthClient.revoke(REFRESH_TOKEN, AppleAuthClient.TOKEN_TYPE_HINT_REFRESH) }
                    count(Outcome.REVOKED) shouldBe 1.0
                }
            }

            `when`("애플이 refresh_token을 주지 않으면") {
                then("access_token으로 해제한다") {
                    stubExchange(subject = APPLE_SUB, refreshToken = null)

                    revoker().revokeQuietly(CODE, APPLE_SUB, Trigger.WITHDRAWAL)

                    verify { appleAuthClient.revoke(ACCESS_TOKEN, AppleAuthClient.TOKEN_TYPE_HINT_ACCESS) }
                }
            }

            `when`("code가 다른 Apple ID의 것이면 (기기에 다른 계정으로 로그인한 경우)") {
                then("엉뚱한 계정을 해제하지 않고 subject_mismatch로 기록한다") {
                    stubExchange(subject = "someone-else")

                    revoker().revokeQuietly(CODE, APPLE_SUB, Trigger.WITHDRAWAL)

                    verify(exactly = 0) { appleAuthClient.revoke(any(), any()) }
                    count(Outcome.SUBJECT_MISMATCH) shouldBe 1.0
                }
            }

            `when`("code 교환이 실패하면 (애플 장애·만료된 code)") {
                then("예외를 삼켜 탈퇴를 막지 않고, failed 기록과 Discord 알림을 남긴다") {
                    every { appleAuthClient.exchangeCode(CODE) } throws
                        AppleAuthApiException(AppleAuthApiException.Reason.UNAVAILABLE, "down")

                    revoker().revokeQuietly(CODE, APPLE_SUB, Trigger.UNLINK)

                    count(Outcome.FAILED, Trigger.UNLINK) shouldBe 1.0
                    verify { discordAlertService.sendOperationalAlert(any(), "AppleAuthApiException", any(), any()) }
                }
            }

            `when`("교환은 됐는데 해제 호출이 실패하면") {
                then("예외를 삼키고 failed로 기록한다") {
                    stubExchange(subject = APPLE_SUB)
                    every { appleAuthClient.revoke(any(), any()) } throws
                        AppleAuthApiException(AppleAuthApiException.Reason.REJECTED, "invalid_client")

                    revoker().revokeQuietly(CODE, APPLE_SUB, Trigger.WITHDRAWAL)

                    count(Outcome.FAILED) shouldBe 1.0
                    count(Outcome.REVOKED) shouldBe 0.0
                }
            }

            `when`("앱이 code를 보내지 않으면 (구버전 앱)") {
                then("애플을 호출하지 않고 missing_code로 기록한다") {
                    revoker().revokeQuietly(null, APPLE_SUB, Trigger.WITHDRAWAL)
                    revoker().revokeQuietly(" ", APPLE_SUB, Trigger.WITHDRAWAL)

                    verify(exactly = 0) { appleAuthClient.exchangeCode(any()) }
                    count(Outcome.MISSING_CODE) shouldBe 2.0
                }
            }

            `when`("키가 아직 설정되지 않았으면") {
                then("애플을 호출하지 않고 disabled로 기록한다") {
                    revoker(AppleTestKeys.disabledProps()).revokeQuietly(CODE, APPLE_SUB, Trigger.WITHDRAWAL)

                    verify(exactly = 0) { appleAuthClient.exchangeCode(any()) }
                    count(Outcome.DISABLED) shouldBe 1.0
                }
            }
        }

        given("애플 계정이 없는 회원이면") {
            `when`("code가 같이 와도") {
                then("아무것도 호출하거나 기록하지 않는다") {
                    revoker().revokeQuietly(CODE, appleSubject = null, Trigger.WITHDRAWAL)

                    verify(exactly = 0) { appleAuthClient.exchangeCode(any()) }
                    meterRegistry.find(AppleAuthorizationRevoker.METRIC_NAME).counters().size shouldBe 0
                }
            }
        }
    }

    private companion object {
        const val CODE = "auth-code"
        const val APPLE_SUB = "apple-sub-001"
        const val ID_TOKEN = "id.token.value"
        const val ACCESS_TOKEN = "access-1"
        const val REFRESH_TOKEN = "refresh-1"
    }
}
