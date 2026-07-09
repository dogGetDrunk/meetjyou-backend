package com.dogGetDrunk.meetjyou.preference

import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.dto.CompanionSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class PreferenceMapperTest : BehaviorSpec() {

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun compPreferenceOf(type: PreferenceType, name: String): CompPreference =
        CompPreference(post = mockk<Post>(relaxed = true), preference = Preference(type, name))

    init {
        given("타입이 서로 다른 CompPreference 목록이 주어졌을 때") {
            `when`("toCompanionSpec을 호출하면") {
                then("타입별로 그룹핑되고 이름순으로 정렬된 CompanionSpec을 반환한다") {
                    val preferences = listOf(
                        compPreferenceOf(PreferenceType.GENDER, Gender.F.name),
                        compPreferenceOf(PreferenceType.AGE, Age.THIRTY.name),
                        compPreferenceOf(PreferenceType.AGE, Age.TEEN.name),
                        compPreferenceOf(PreferenceType.DIET, Diet.VEGAN.name),
                        compPreferenceOf(PreferenceType.ETC, Etc.SMOKE.name),
                    )

                    val spec = preferences.toCompanionSpec()

                    spec.gender shouldBe listOf(Gender.F)
                    spec.age shouldBe listOf(Age.TEEN, Age.THIRTY)
                    spec.diet shouldBe listOf(Diet.VEGAN)
                    spec.etc shouldBe listOf(Etc.SMOKE)
                    spec.personalities shouldBe emptyList()
                    spec.travelStyles shouldBe emptyList()
                }
            }
        }

        given("빈 목록이 주어졌을 때") {
            `when`("toCompanionSpec을 호출하면") {
                then("모든 필드가 빈 리스트인 CompanionSpec을 반환한다") {
                    val spec = emptyList<CompPreference>().toCompanionSpec()

                    spec shouldBe CompanionSpec()
                }
            }
        }

        given("알 수 없는 enum 이름이 섞여 있을 때") {
            `when`("strict = true (기본값)이면") {
                then("예외를 던진다") {
                    val preferences = listOf(compPreferenceOf(PreferenceType.GENDER, "UNKNOWN"))

                    shouldThrow<IllegalStateException> {
                        preferences.toCompanionSpec()
                    }
                }
            }

            `when`("strict = false이면") {
                then("알 수 없는 값은 무시하고 나머지만 반환한다") {
                    val preferences = listOf(
                        compPreferenceOf(PreferenceType.GENDER, "UNKNOWN"),
                        compPreferenceOf(PreferenceType.GENDER, Gender.M.name),
                    )

                    val spec = preferences.toCompanionSpec(strict = false)

                    spec.gender shouldBe listOf(Gender.M)
                }
            }
        }
    }
}
