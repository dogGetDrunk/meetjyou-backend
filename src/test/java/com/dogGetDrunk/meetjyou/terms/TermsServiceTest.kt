package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

class TermsServiceTest : BehaviorSpec() {
    private val termsRepository = mockk<TermsRepository>(relaxed = true)
    private val userTermsRepository = mockk<UserTermsRepository>(relaxed = true)
    private val termsContentUrlGenerator = mockk<TermsContentUrlGenerator>(relaxed = true)

    private val sut = TermsService(
        termsRepository,
        userTermsRepository,
        termsContentUrlGenerator,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        // ── saveUserTerms ────────────────────────────────────────────────────

        given("saveUserTerms 호출 시") {
            val user = UserFixtures.user()

            `when`("agreedTerms에 마케팅 약관 타입이 포함되면") {
                then("User의 marketingSnsConsented/marketingEmailConsented가 함께 갱신된다") {
                    val agreedTerms = listOf(
                        termsOf(TermsType.TERMS_OF_SERVICE),
                        termsOf(TermsType.MARKETING_SNS_EVENTS),
                    )

                    sut.saveUserTerms(user, agreedTerms)

                    user.marketingSnsConsented shouldBe true
                    user.marketingEmailConsented shouldBe false
                }
            }

            `when`("agreedTerms에 마케팅 약관 타입이 없으면") {
                then("User의 marketingSnsConsented/marketingEmailConsented는 false로 유지된다") {
                    val agreedTerms = listOf(termsOf(TermsType.TERMS_OF_SERVICE))

                    sut.saveUserTerms(user, agreedTerms)

                    user.marketingSnsConsented shouldBe false
                    user.marketingEmailConsented shouldBe false
                }
            }
        }

        // ── recordConsentChange ──────────────────────────────────────────────

        given("recordConsentChange 호출 시") {
            val user = UserFixtures.user()

            `when`("이전 동의 이력이 없고 agreed=true로 호출되면") {
                then("AGREED 액션으로 UserTerms가 저장된다") {
                    val activeTerms = termsOf(TermsType.MARKETING_SNS_EVENTS)
                    every {
                        userTermsRepository.findTopByUser_IdAndTerms_TypeOrderByIdDesc(user.id, TermsType.MARKETING_SNS_EVENTS)
                    } returns null
                    every {
                        termsRepository.findByTypeAndStatus(TermsType.MARKETING_SNS_EVENTS, TermsStatus.ACTIVE)
                    } returns activeTerms

                    val saved = slot<UserTerms>()
                    every { userTermsRepository.save(capture(saved)) } answers { saved.captured }

                    sut.recordConsentChange(user, TermsType.MARKETING_SNS_EVENTS, true)

                    saved.captured.action shouldBe TermsAgreementAction.AGREED
                }
            }

            `when`("최신 이력의 action이 요청과 동일하면") {
                then("새 UserTerms row를 저장하지 않는다") {
                    val activeTerms = termsOf(TermsType.MARKETING_EMAIL_EVENTS)
                    val latest = UserTerms(terms = activeTerms, user = user, action = TermsAgreementAction.WITHDRAWN)
                    every {
                        userTermsRepository.findTopByUser_IdAndTerms_TypeOrderByIdDesc(user.id, TermsType.MARKETING_EMAIL_EVENTS)
                    } returns latest

                    sut.recordConsentChange(user, TermsType.MARKETING_EMAIL_EVENTS, false)

                    verify(exactly = 0) { userTermsRepository.save(any()) }
                }
            }

            `when`("해당 타입의 ACTIVE 약관이 존재하지 않으면") {
                then("TermsNotFoundException을 던진다") {
                    every {
                        userTermsRepository.findTopByUser_IdAndTerms_TypeOrderByIdDesc(user.id, TermsType.MARKETING_SNS_EVENTS)
                    } returns null
                    every {
                        termsRepository.findByTypeAndStatus(TermsType.MARKETING_SNS_EVENTS, TermsStatus.ACTIVE)
                    } returns null

                    shouldThrow<TermsNotFoundException> {
                        sut.recordConsentChange(user, TermsType.MARKETING_SNS_EVENTS, true)
                    }
                }
            }
        }
    }

    private fun termsOf(type: TermsType): Terms = Terms(
        type = type,
        version = "1.0",
        displayText = "$type display text",
        required = false,
        contentObjectKey = "terms/${type.name.lowercase()}.pdf",
        contentHash = "hash-${type.name}",
    )
}
