package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.exception.business.auth.LoadTestTokenForbiddenException
import com.dogGetDrunk.meetjyou.config.property.LoadTestTokenProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class LoadTestTokenServiceTest : BehaviorSpec() {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val jwtProvider = mockk<JwtProvider>(relaxed = true)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        val secret = "a".repeat(32)
        val syntheticUser = User(
            email = "synthetic-loadtest@internal.meetjyou",
            nickname = "loadtest",
            authProvider = AuthProvider.KAKAO,
            externalId = "loadtest-synthetic",
        )

        beforeEach { clearAllMocks() }

        given("load-test-token 기능이 꺼져있으면") {
            `when`("올바른 secret으로 요청해도") {
                then("거부된다") {
                    val sut = LoadTestTokenService(
                        LoadTestTokenProperties(enabled = false, secret = secret),
                        userRepository,
                        jwtProvider,
                    )

                    shouldThrow<LoadTestTokenForbiddenException> { sut.issueToken(secret) }
                }
            }
        }

        given("load-test-token 기능이 켜져있으면") {
            val props = LoadTestTokenProperties(enabled = true, secret = secret, ttlMillis = 300_000)
            val sut = LoadTestTokenService(props, userRepository, jwtProvider)

            `when`("잘못된 secret으로 요청하면") {
                then("거부된다") {
                    shouldThrow<LoadTestTokenForbiddenException> { sut.issueToken("wrong-secret") }
                }
            }

            `when`("secret이 없으면") {
                then("거부된다") {
                    shouldThrow<LoadTestTokenForbiddenException> { sut.issueToken(null) }
                }
            }

            `when`("올바른 secret으로 요청하면") {
                then("토큰이 발급되고 최초 1회만 계정을 생성한다") {
                    every { userRepository.findByEmail(any()) } returns null andThen syntheticUser
                    every { userRepository.save(any()) } returns syntheticUser
                    every {
                        jwtProvider.generateAccessToken(syntheticUser.uuid, syntheticUser.email, syntheticUser.role, props.ttlMillis)
                    } returns "issued-token"

                    val first = sut.issueToken(secret)
                    first.accessToken shouldBe "issued-token"
                    verify(exactly = 1) { userRepository.save(any()) }

                    sut.issueToken(secret)
                    verify(exactly = 1) { userRepository.save(any()) }
                }
            }
        }
    }
}
