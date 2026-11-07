package com.dogGetDrunk.meetjyou.convention

import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantRepository
import com.dogGetDrunk.meetjyou.notification.push.PushTokenRepository
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * AT-007-1: confirms the 12 unused repository methods removed in #231 stay removed.
 *
 * #231 정리 확인용, 같은 이름 메서드가 다시 필요하면 이 테스트 삭제.
 * Reflection name comparison only (overloads included); the methods are never called.
 */
class UnusedRepositoryMethodRemovalTest : BehaviorSpec({
    val removedMethodsByRepository: Map<Class<*>, Set<String>> = mapOf(
        PartyRepository::class.java to setOf(
            "findAllByPlan_Uuid",
            "existsByPlan_Uuid",
        ),
        UserPartyRepository::class.java to setOf(
            "findAllByUser_Uuid",
            "findAllByParty_Uuid",
            "existsByParty_UuidAndUser_Uuid",
            "existsByParty_UuidAndUser_UuidAndMemberStatus",
        ),
        UserPreferenceRepository::class.java to setOf(
            "findPreferenceByUserIdAndType",
            "findPreferencesByUserIdAndType",
        ),
        PreferenceRepository::class.java to setOf(
            "findByName",
        ),
        ChatParticipantRepository::class.java to setOf(
            "findAllByRoom_UuidAndUser_UuidIn",
        ),
        PushTokenRepository::class.java to setOf(
            "findAllByUserIdAndActiveTrue",
            "existsByUserIdAndToken",
        ),
    )

    Given("AT-007-1 (#231 정리 확인용, 같은 이름 메서드가 다시 필요하면 이 테스트 삭제) 리포지토리 인터페이스") {
        removedMethodsByRepository.forEach { (repository, removedNames) ->
            When("${repository.simpleName}의 declaredMethods 이름을 조회하면") {
                val declaredNames = repository.declaredMethods.map { it.name }.toSet()

                Then("AT-007-1: 삭제 대상 ${removedNames.size}개 이름이 하나도 없다") {
                    declaredNames.intersect(removedNames).shouldBeEmpty()
                }
            }
        }
    }
})
