package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.refreshtoken.RefreshTokenRepository
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class UserServiceTest : BehaviorSpec() {
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val userPreferenceRepository = mockk<UserPreferenceRepository>(relaxed = true)

    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val termsService = mockk<TermsService>(relaxed = true)
    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)

    private val sut = UserService(
        userRepository,
        preferenceRepository,
        userPreferenceRepository,
        currentUserProvider,
        termsService,
        refreshTokenRepository,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        // ── confirmProfileImage ──────────────────────────────────────────────

        given("confirmProfileImage 호출 시") {
            val user = UserFixtures.user()
            val uuid = user.uuid

            beforeEach {
                every { currentUserProvider.uuid } returns uuid
                every { currentUserProvider.user } returns user
            }

            `when`("정상적으로 호출되면") {
                then("hasProfileImage가 true로 설정된다") {
                    sut.confirmProfileImage()

                    user.hasProfileImage shouldBe true
                }
            }

            `when`("유저가 존재하지 않으면") {
                then("UserNotFoundException을 던진다") {
                    every { currentUserProvider.user } throws UserNotFoundException(uuid)

                    shouldThrow<UserNotFoundException> { sut.confirmProfileImage() }
                }
            }
        }

        // ── clearProfileImage ────────────────────────────────────────────────

        given("clearProfileImage 호출 시") {
            val user = UserFixtures.user().apply {
                hasProfileImage = true
            }
            val uuid = user.uuid

            beforeEach {
                every { currentUserProvider.uuid } returns uuid
                every { currentUserProvider.user } returns user
            }

            `when`("정상적으로 호출되면") {
                then("hasProfileImage가 false로 초기화된다") {
                    sut.clearProfileImage()

                    user.hasProfileImage shouldBe false
                }
            }
        }

        // ── updateMarketingConsent ───────────────────────────────────────────

        given("updateMarketingConsent 호출 시") {
            val user = UserFixtures.user()
            val uuid = user.uuid

            beforeEach {
                every { currentUserProvider.uuid } returns uuid
                every { currentUserProvider.user } returns user
            }

            `when`("sns=true, email=false로 호출되면") {
                then("marketingSnsConsented와 marketingEmailConsented가 각각 반영되고 타입별로 이력이 기록된다") {
                    sut.updateMarketingConsent(snsConsented = true, emailConsented = false)

                    user.marketingSnsConsented shouldBe true
                    user.marketingEmailConsented shouldBe false
                    verify { termsService.recordConsentChange(user, TermsType.MARKETING_SNS_EVENTS, true) }
                    verify { termsService.recordConsentChange(user, TermsType.MARKETING_EMAIL_EVENTS, false) }
                }
            }

            `when`("두 값 모두 false로 호출되면") {
                then("marketingSnsConsented와 marketingEmailConsented가 모두 false로 유지된다") {
                    user.marketingSnsConsented = true
                    user.marketingEmailConsented = true
                    sut.updateMarketingConsent(snsConsented = false, emailConsented = false)

                    user.marketingSnsConsented shouldBe false
                    user.marketingEmailConsented shouldBe false
                }
            }

            `when`("유저가 존재하지 않으면") {
                then("UserNotFoundException을 던진다") {
                    every { currentUserProvider.user } throws UserNotFoundException(uuid)

                    shouldThrow<UserNotFoundException> {
                        sut.updateMarketingConsent(snsConsented = true, emailConsented = true)
                    }
                }
            }
        }

        // ── getUserProfile (hasProfileImage pass-through) ─────────────────────

        given("getUserProfile 호출 시") {
            fun requiredPreferences(user: User): List<UserPreference> = listOf(
                UserPreference(user, Preference(type = PreferenceType.GENDER, name = "SOME_VALUE")),
                UserPreference(user, Preference(type = PreferenceType.AGE, name = "SOME_VALUE")),
            )

            `when`("유저에 hasProfileImage가 false인 경우") {
                then("응답의 hasProfileImage도 false이다") {
                    val user = UserFixtures.user()

                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { userPreferenceRepository.findAllByUser_IdIn(listOf(user.id)) } returns requiredPreferences(user)

                    val result = sut.getUserProfile(user.uuid)

                    result.hasProfileImage shouldBe false
                }
            }

            `when`("유저에 hasProfileImage가 true인 경우") {
                then("응답의 hasProfileImage도 true이다") {
                    val user = UserFixtures.user().apply {
                        hasProfileImage = true
                    }

                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { userPreferenceRepository.findAllByUser_IdIn(listOf(user.id)) } returns requiredPreferences(user)

                    val result = sut.getUserProfile(user.uuid)

                    result.hasProfileImage shouldBe true
                }
            }
        }

        // ── getAllUsersProfile ───────────────────────────────────────────────

        given("getAllUsersProfile 호출 시") {
            `when`("페이지 요청이 들어오면") {
                then("userRepository.findAll(pageable)로 페이지네이션되고, preference는 유저 수와 무관하게 배치로 한 번만 조회한다") {
                    val user = UserFixtures.user()
                    val pageable = PageRequest.of(0, 20)
                    val page = PageImpl(listOf(user))
                    val prefs = listOf(
                        UserPreference(user, Preference(type = PreferenceType.GENDER, name = "SOME_VALUE")),
                        UserPreference(user, Preference(type = PreferenceType.AGE, name = "SOME_VALUE")),
                    )

                    every { userRepository.findAll(pageable) } returns page
                    every { userPreferenceRepository.findAllByUser_IdIn(listOf(user.id)) } returns prefs

                    val result = sut.getAllUsersProfile(pageable)

                    result.content.single().uuid shouldBe user.uuid
                    verify(exactly = 1) { userRepository.findAll(pageable) }
                    verify(exactly = 1) { userPreferenceRepository.findAllByUser_IdIn(listOf(user.id)) }
                }
            }
        }

        // ── withdrawUser ─────────────────────────────────────────────────────

        given("withdrawUser 호출 시") {
            val user = UserFixtures.user()

            beforeEach {
                every { currentUserProvider.uuid } returns user.uuid
                every { userRepository.findByUuid(user.uuid) } returns user
            }

            `when`("post/plan 등 연관 데이터가 있어 하드 삭제라면 FK 위반이 날 상황이어도") {
                then("하드 삭제 대신 status를 DELETED로 바꾸고 리프레시 토큰을 모두 무효화한다") {
                    sut.withdrawUser()

                    user.status shouldBe UserStatus.DELETED
                    verify { refreshTokenRepository.revokeAllByUser(user) }
                    verify(exactly = 0) { userRepository.deleteByUuid(any()) }
                }
            }

            `when`("유저가 존재하지 않으면") {
                then("UserNotFoundException을 던진다") {
                    every { userRepository.findByUuid(user.uuid) } returns null

                    shouldThrow<UserNotFoundException> { sut.withdrawUser() }
                }
            }
        }
    }
}
