package com.dogGetDrunk.meetjyou.config

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import org.springframework.mock.env.MockEnvironment

class CorsConfigTest : BehaviorSpec() {

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        given("CORS 설정을 구성할 때") {
            `when`("dev 프로필이 활성화된 경우") {
                then("localhost origin을 허용한다") {
                    val environment = MockEnvironment().apply { setActiveProfiles("dev") }
                    val config = CorsConfig("https://meetjyou.com", environment)

                    val patterns = config.corsConfigurationSource()
                        .getCorsConfiguration(org.springframework.mock.web.MockHttpServletRequest())
                        ?.allowedOriginPatterns

                    patterns?.shouldContain("http://localhost:*")
                }
            }

            `when`("release 프로필이 활성화된 경우") {
                then("localhost origin을 허용하지 않는다") {
                    val environment = MockEnvironment().apply { setActiveProfiles("release") }
                    val config = CorsConfig("https://meetjyou.com", environment)

                    val patterns = config.corsConfigurationSource()
                        .getCorsConfiguration(org.springframework.mock.web.MockHttpServletRequest())
                        ?.allowedOriginPatterns

                    patterns?.shouldNotContain("http://localhost:*")
                    patterns?.shouldContain("https://meetjyou.com")
                }
            }
        }
    }
}
