package com.dogGetDrunk.meetjyou.terms

import com.dogGetDrunk.meetjyou.cloud.oracle.dto.ParResponse
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.TermsNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.terms.TermsContentVerificationException
import com.dogGetDrunk.meetjyou.notification.event.TermsReconsentEvent
import com.dogGetDrunk.meetjyou.terms.dto.PublishTermsRequest
import com.dogGetDrunk.meetjyou.terms.dto.TermsUploadPar
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
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

class TermsServiceTest : BehaviorSpec() {
    private val termsRepository = mockk<TermsRepository>(relaxed = true)
    private val userTermsRepository = mockk<UserTermsRepository>(relaxed = true)
    private val termsContentUrlGenerator = mockk<TermsContentUrlGenerator>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val sut = TermsService(
        termsRepository,
        userTermsRepository,
        termsContentUrlGenerator,
        publisher,
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

        // ── publishTerms ─────────────────────────────────────────────────────

        given("publishTerms 호출 시") {
            val request = PublishTermsRequest(
                type = TermsType.TERMS_OF_SERVICE,
                version = "2.0",
                displayText = "이용약관",
                required = true,
                contentHash = "hash-v2",
            )
            val objectKey = TermsType.TERMS_OF_SERVICE.toObjectKey(request.version)

            `when`("본문 오브젝트 검증에 실패하면") {
                then("TermsContentVerificationException을 던지고 약관을 저장하지 않는다") {
                    every {
                        termsContentUrlGenerator.verifyContent(objectKey, request.contentHash)
                    } returns false

                    shouldThrow<TermsContentVerificationException> {
                        sut.publishTerms(request)
                    }

                    verify(exactly = 0) { termsRepository.save(any()) }
                }
            }

            `when`("동일 타입의 기존 활성 약관이 없으면") {
                then("새 약관만 저장하고 재동의 이벤트는 발행하지 않는다") {
                    every {
                        termsContentUrlGenerator.verifyContent(objectKey, request.contentHash)
                    } returns true
                    every {
                        termsRepository.findByTypeAndStatus(TermsType.TERMS_OF_SERVICE, TermsStatus.ACTIVE)
                    } returns null
                    every { termsRepository.save(any()) } answers { firstArg() }

                    sut.publishTerms(request)

                    verify(exactly = 0) { publisher.publishEvent(any<TermsReconsentEvent>()) }
                }
            }

            `when`("동일 타입의 기존 활성 약관이 있으면") {
                then("기존 약관을 INACTIVE로 전환하고 재동의 이벤트를 발행한다") {
                    val previousTerms = termsOf(TermsType.TERMS_OF_SERVICE)
                    every {
                        termsContentUrlGenerator.verifyContent(objectKey, request.contentHash)
                    } returns true
                    every {
                        termsRepository.findByTypeAndStatus(TermsType.TERMS_OF_SERVICE, TermsStatus.ACTIVE)
                    } returns previousTerms
                    every { termsRepository.save(any()) } answers { firstArg() }

                    sut.publishTerms(request)

                    previousTerms.status shouldBe TermsStatus.INACTIVE
                    verify(exactly = 1) { publisher.publishEvent(any<TermsReconsentEvent>()) }
                }
            }
        }

        // ── createContentUploadPar ──────────────────────────────────────────

        given("createContentUploadPar 호출 시") {
            `when`("타입과 버전이 주어지면") {
                then("생성기가 반환한 업로드 PAR를 그대로 반환한다") {
                    val uploadPar = TermsUploadPar(
                        objectKey = "terms/service/v2.0.html",
                        par = ParResponse(
                            url = "https://example.com/upload",
                            httpMethod = "PUT",
                            expiresAt = Instant.now(),
                        ),
                    )
                    every {
                        termsContentUrlGenerator.generateUploadPar(TermsType.TERMS_OF_SERVICE, "2.0")
                    } returns uploadPar

                    val result = sut.createContentUploadPar(TermsType.TERMS_OF_SERVICE, "2.0")

                    result shouldBe uploadPar
                }
            }
        }
    }

    private fun termsOf(type: TermsType): Terms = Terms(
        type = type,
        version = "1.0",
        displayText = "$type display text",
        required = false,
        contentObjectKey = type.toObjectKey("1.0"),
        contentHash = "hash-${type.name}",
    )
}
