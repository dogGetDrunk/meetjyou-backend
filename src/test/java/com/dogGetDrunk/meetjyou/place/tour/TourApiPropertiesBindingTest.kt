package com.dogGetDrunk.meetjyou.place.tour

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.core.io.ClassPathResource

/**
 * AT-003 key binding (C-r1-01): the deploy sets PLACE_TOUR_SERVICE_KEY, which relaxed binding alone
 * would not map to `place.tour.service-key` — application.yml must spell the placeholder out.
 */
class TourApiPropertiesBindingTest : BehaviorSpec() {

    private fun bind(env: Map<String, Any>): TourApiProperties {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(SystemEnvironmentPropertySource("test-env", env))
        YamlPropertySourceLoader().load("application.yml", ClassPathResource("application.yml"))
            .forEach { environment.propertySources.addLast(it) }
        return Binder.get(environment).bind("place.tour", TourApiProperties::class.java)
            .orElseGet { TourApiProperties() }
    }

    init {
        given("application.yml") {
            then("환경 변수 PLACE_TOUR_SERVICE_KEY가 서비스키로 들어간다") {
                val props = bind(mapOf("PLACE_TOUR_SERVICE_KEY" to "ab+cd/ef=="))
                props.serviceKey shouldBe "ab+cd/ef=="
                props.enabled shouldBe true
            }
            then("환경 변수가 없으면 빈 키라 비활성") {
                val props = bind(emptyMap())
                props.serviceKey shouldBe ""
                props.enabled shouldBe false
            }
            then("toString에 키가 나오지 않는다") {
                bind(mapOf("PLACE_TOUR_SERVICE_KEY" to "secret-key")).toString() shouldNotContain "secret-key"
            }
        }
    }
}
