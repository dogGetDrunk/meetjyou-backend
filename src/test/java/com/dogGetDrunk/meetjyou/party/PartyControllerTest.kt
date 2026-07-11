package com.dogGetDrunk.meetjyou.party

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.web.PageableDefault

class PartyControllerTest : BehaviorSpec({

    given("getPartiesByPlanUuid의 기본 페이지네이션 정렬 설정은") {
        `when`("파라미터를 확인하면") {
            then("Party 엔티티에 실제로 존재하는 프로퍼티(createdAt)를 가리켜야 한다") {
                val method = PartyController::class.java.methods
                    .first { it.name == "getPartiesByPlanUuid" }
                val pageableParam = method.parameters.first { it.isAnnotationPresent(ParameterObject::class.java) }
                val sort = pageableParam.getAnnotation(PageableDefault::class.java).sort

                // PartyRepository.findAllByPlan_Uuid queries Party directly, not UserParty,
                // so the sort property must NOT be prefixed with "party." (copy-paste regression guard).
                sort shouldBe arrayOf("createdAt")
            }
        }
    }
})
