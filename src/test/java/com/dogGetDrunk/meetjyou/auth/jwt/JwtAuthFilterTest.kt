package com.dogGetDrunk.meetjyou.auth.jwt

import com.dogGetDrunk.meetjyou.common.exception.business.jwt.InvalidJwtException
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserStatus
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthFilterTest : BehaviorSpec() {

    private val jwtProvider = mockk<JwtProvider>()
    private val userRepository = mockk<UserRepository>()
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val filter = JwtAuthFilter(jwtProvider, objectMapper, userRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach {
            clearAllMocks()
            SecurityContextHolder.clearContext()
        }

        given("유효한 access token으로 요청이 들어올 때") {
            val user = UserFixtures.user()

            `when`("계정 status가 NORMAL이면") {
                then("SecurityContext에 인증 정보를 설정하고 체인을 통과시킨다") {
                    every { jwtProvider.extractToken(any()) } returns "valid.token"
                    every { jwtProvider.validateTokenOrThrow("valid.token") } returns Unit
                    every { jwtProvider.getUserUuid("valid.token") } returns user.uuid
                    every { jwtProvider.getUsername("valid.token") } returns user.email
                    every { jwtProvider.getRole("valid.token") } returns Role.USER
                    every { userRepository.findByUuid(user.uuid) } returns user

                    var passed = false
                    val chain = FilterChain { _, _ -> passed = true }
                    val request = MockHttpServletRequest("GET", "/api/v1/users/me/profile")
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, chain)

                    passed shouldBe true
                    SecurityContextHolder.getContext().authentication.isAuthenticated shouldBe true
                }
            }

            `when`("계정 status가 DELETED(탈퇴)이면") {
                then("401을 반환하고 체인을 통과시키지 않는다") {
                    user.status = UserStatus.DELETED
                    every { jwtProvider.extractToken(any()) } returns "valid.token"
                    every { jwtProvider.validateTokenOrThrow("valid.token") } returns Unit
                    every { jwtProvider.getUserUuid("valid.token") } returns user.uuid
                    every { jwtProvider.getUsername("valid.token") } returns user.email
                    every { jwtProvider.getRole("valid.token") } returns Role.USER
                    every { userRepository.findByUuid(user.uuid) } returns user

                    var passed = false
                    val chain = FilterChain { _, _ -> passed = true }
                    val request = MockHttpServletRequest("GET", "/api/v1/users/me/profile")
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, chain)

                    passed shouldBe false
                    response.status shouldBe 401
                    SecurityContextHolder.getContext().authentication shouldBe null
                }
            }

            `when`("토큰의 유저를 찾을 수 없으면") {
                then("401을 반환하고 체인을 통과시키지 않는다") {
                    every { jwtProvider.extractToken(any()) } returns "valid.token"
                    every { jwtProvider.validateTokenOrThrow("valid.token") } returns Unit
                    every { jwtProvider.getUserUuid("valid.token") } returns user.uuid
                    every { jwtProvider.getUsername("valid.token") } returns user.email
                    every { jwtProvider.getRole("valid.token") } returns Role.USER
                    every { userRepository.findByUuid(user.uuid) } returns null

                    var passed = false
                    val chain = FilterChain { _, _ -> passed = true }
                    val request = MockHttpServletRequest("GET", "/api/v1/users/me/profile")
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, chain)

                    passed shouldBe false
                    response.status shouldBe 401
                }
            }
        }

        given("유효하지 않은 access token으로 요청이 들어올 때") {
            `when`("토큰 검증에 실패하면") {
                then("401을 반환하고 체인을 통과시키지 않는다") {
                    every { jwtProvider.extractToken(any()) } returns "invalid.token"
                    every { jwtProvider.validateTokenOrThrow("invalid.token") } throws InvalidJwtException()

                    var passed = false
                    val chain = FilterChain { _, _ -> passed = true }
                    val request = MockHttpServletRequest("GET", "/api/v1/users/me/profile")
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, chain)

                    passed shouldBe false
                    response.status shouldBe 401
                }
            }
        }
    }
}
