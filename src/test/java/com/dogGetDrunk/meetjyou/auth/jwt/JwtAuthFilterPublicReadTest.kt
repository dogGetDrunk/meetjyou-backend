package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.auth.CustomUserPrincipal
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.CustomExpiredJwtException
import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

/**
 * AT-001-1: public GET endpoints proceed anonymously when the bearer token fails,
 * every other request keeps the existing error response.
 */
class JwtAuthFilterPublicReadTest : BehaviorSpec() {

    private val jwtProvider = mockk<JwtProvider>()
    private val userRepository = mockk<UserRepository>()
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private lateinit var filter: JwtAuthFilter

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private class Outcome(val passed: Boolean, val response: MockHttpServletResponse)

    private fun run(method: String, path: String): Outcome {
        var passed = false
        val chain = FilterChain { _, _ -> passed = true }
        val response = MockHttpServletResponse()
        filter.doFilter(MockHttpServletRequest(method, path), response, chain)
        return Outcome(passed, response)
    }

    private fun stubTokenFailure(error: Exception) {
        every { jwtProvider.extractToken(any()) } returns TOKEN
        every { jwtProvider.validateAccessTokenOrThrow(TOKEN) } throws error
    }

    private fun stubValidTokenFor(user: User) {
        every { jwtProvider.extractToken(any()) } returns TOKEN
        every { jwtProvider.validateAccessTokenOrThrow(TOKEN) } returns Unit
        every { jwtProvider.getUserUuid(TOKEN) } returns user.uuid
        every { userRepository.findByUuid(user.uuid) } returns user
    }

    private fun Outcome.shouldProceedAnonymously() {
        passed shouldBe true
        response.status shouldBe 200
        response.contentAsString.shouldBeEmpty()
        SecurityContextHolder.getContext().authentication shouldBe null
    }

    private fun Outcome.shouldBeRejectedWith(code: ErrorCode) {
        passed shouldBe false
        response.status shouldBe 401
        response.contentAsString shouldContain code.name
    }

    init {
        beforeEach {
            clearAllMocks()
            SecurityContextHolder.clearContext()
            // A fresh cache per test: Kotest may run several leaves on one spec instance.
            filter = JwtAuthFilter(jwtProvider, objectMapper, AuthenticatedUserCache(userRepository))
        }

        given("AT-001-1 공개 GET 요청에 실패하는 토큰이 붙었을 때") {
            PUBLIC_GET_PATHS.forEach { path ->
                `when`("$path 에서 토큰이 만료되었으면") {
                    then("AT-001-1 체인을 통과시키고 응답·인증 정보를 비워 둔다") {
                        stubTokenFailure(CustomExpiredJwtException(null))
                        run("GET", path).shouldProceedAnonymously()
                    }
                }

                `when`("$path 에서 refresh 토큰 등 access 토큰이 아니면") {
                    then("AT-001-1 체인을 통과시키고 응답·인증 정보를 비워 둔다") {
                        stubTokenFailure(InvalidJwtException(message = "Not an access token"))
                        run("GET", path).shouldProceedAnonymously()
                    }
                }

                `when`("$path 에서 토큰의 유저가 탈퇴했으면") {
                    then("AT-001-1 체인을 통과시키고 응답·인증 정보를 비워 둔다") {
                        val user = UserFixtures.user()
                        user.status = UserStatus.DELETED
                        stubValidTokenFor(user)
                        run("GET", path).shouldProceedAnonymously()
                    }
                }
            }
        }

        given("AT-001-1 공개 GET 요청에 유효한 토큰이 붙었을 때") {
            PUBLIC_GET_PATHS.forEach { path ->
                `when`("$path 의 유저가 NORMAL이면") {
                    then("AT-001-1 인증 정보를 설정하고 체인을 통과시킨다") {
                        val user = UserFixtures.user()
                        stubValidTokenFor(user)

                        val outcome = run("GET", path)

                        outcome.passed shouldBe true
                        val principal = SecurityContextHolder.getContext().authentication.principal
                        (principal as CustomUserPrincipal).uuid shouldBe user.uuid
                    }
                }
            }
        }

        given("AT-001-1 비공개 GET 요청에 실패하는 토큰이 붙었을 때") {
            `when`("토큰이 만료되었으면") {
                then("AT-001-1 체인 없이 401 EXPIRED_TOKEN을 반환한다") {
                    stubTokenFailure(CustomExpiredJwtException(null))
                    run("GET", PRIVATE_GET_PATH).shouldBeRejectedWith(ErrorCode.EXPIRED_TOKEN)
                }
            }

            `when`("토큰 형식이 잘못되었으면") {
                then("AT-001-1 체인 없이 401 INVALID_JWT를 반환한다") {
                    stubTokenFailure(InvalidJwtException())
                    run("GET", PRIVATE_GET_PATH).shouldBeRejectedWith(ErrorCode.INVALID_JWT)
                }
            }
        }

        given("AT-001-1 공개 경로의 GET 외 메서드에 만료 토큰이 붙었을 때") {
            `when`("POST $PUBLIC_POST_PATH 이면") {
                then("AT-001-1 체인 없이 401 EXPIRED_TOKEN을 반환한다") {
                    stubTokenFailure(CustomExpiredJwtException(null))
                    run("POST", PUBLIC_POST_PATH).shouldBeRejectedWith(ErrorCode.EXPIRED_TOKEN)
                }
            }
        }
    }

    companion object {
        private const val TOKEN = "failing.token"
        private const val PRIVATE_GET_PATH = "/api/v1/users/me/profile"
        private const val PUBLIC_POST_PATH = "/api/v1/notices"
        private val PUBLIC_GET_PATHS = listOf("/api/v1/notices", "/api/v1/ios/versions/check")
    }
}
