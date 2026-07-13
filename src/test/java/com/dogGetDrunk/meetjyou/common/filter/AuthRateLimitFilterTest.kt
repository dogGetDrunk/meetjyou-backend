package com.dogGetDrunk.meetjyou.common.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AuthRateLimitFilterTest : BehaviorSpec() {

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        given("로그인 엔드포인트에 반복 요청이 들어올 때") {
            `when`("허용 한도(5회) 이내면") {
                then("매번 필터 체인을 통과시킨다") {
                    val filter = AuthRateLimitFilter(ObjectMapper().registerKotlinModule())
                    var passedCount = 0
                    val chain = FilterChain { _, _ -> passedCount++ }

                    repeat(5) {
                        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
                        request.remoteAddr = "127.0.0.1"
                        val response = MockHttpServletResponse()

                        filter.doFilter(request, response, chain)
                    }

                    passedCount shouldBe 5
                }
            }

            `when`("허용 한도(5회)를 초과하면") {
                then("6번째 요청부터 429를 반환하고 체인을 통과시키지 않는다") {
                    val filter = AuthRateLimitFilter(ObjectMapper().registerKotlinModule())
                    var passedCount = 0
                    val chain = FilterChain { _, _ -> passedCount++ }

                    lateinit var lastResponse: MockHttpServletResponse
                    repeat(6) {
                        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
                        request.remoteAddr = "127.0.0.1"
                        lastResponse = MockHttpServletResponse()

                        filter.doFilter(request, lastResponse, chain)
                    }

                    passedCount shouldBe 5
                    lastResponse.status shouldBe 429
                }
            }

            `when`("다른 IP에서 요청하면") {
                then("한도가 소진된 IP와 무관하게 통과시킨다") {
                    val filter = AuthRateLimitFilter(ObjectMapper().registerKotlinModule())
                    var passedCount = 0
                    val chain = FilterChain { _, _ -> passedCount++ }

                    repeat(5) {
                        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
                        request.remoteAddr = "127.0.0.1"
                        filter.doFilter(request, MockHttpServletResponse(), chain)
                    }

                    val otherIpRequest = MockHttpServletRequest("POST", "/api/v1/auth/login")
                    otherIpRequest.remoteAddr = "192.168.0.1"
                    val otherIpResponse = MockHttpServletResponse()
                    filter.doFilter(otherIpRequest, otherIpResponse, chain)

                    passedCount shouldBe 6
                    otherIpResponse.status shouldBe 200
                }
            }
        }

        given("관리자 승격 엔드포인트에 반복 요청이 들어올 때") {
            `when`("허용 한도(5회)를 초과하면") {
                then("6번째 요청부터 429를 반환하고 체인을 통과시키지 않는다") {
                    val filter = AuthRateLimitFilter(ObjectMapper().registerKotlinModule())
                    var passedCount = 0
                    val chain = FilterChain { _, _ -> passedCount++ }

                    lateinit var lastResponse: MockHttpServletResponse
                    repeat(6) {
                        val request = MockHttpServletRequest("POST", "/api/v1/auth/promote-admin")
                        request.remoteAddr = "127.0.0.1"
                        lastResponse = MockHttpServletResponse()

                        filter.doFilter(request, lastResponse, chain)
                    }

                    passedCount shouldBe 5
                    lastResponse.status shouldBe 429
                }
            }
        }

        given("한도 대상이 아닌 경로에 요청이 들어올 때") {
            `when`("반복 요청해도") {
                then("항상 필터 체인을 통과시킨다") {
                    val filter = AuthRateLimitFilter(ObjectMapper().registerKotlinModule())
                    var passedCount = 0
                    val chain = FilterChain { _, _ -> passedCount++ }

                    repeat(20) {
                        val request = MockHttpServletRequest("GET", "/api/v1/notices")
                        request.remoteAddr = "127.0.0.1"
                        filter.doFilter(request, MockHttpServletResponse(), chain)
                    }

                    passedCount shouldBe 20
                }
            }
        }
    }
}
