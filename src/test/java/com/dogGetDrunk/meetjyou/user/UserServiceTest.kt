package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.social.SocialPrincipal
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.user.DuplicateNicknameException
import com.dogGetDrunk.meetjyou.common.exception.business.user.UserAlreadyExistsException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PreferenceNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.preference.Age
import com.dogGetDrunk.meetjyou.preference.Gender
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import com.dogGetDrunk.meetjyou.preference.UserPreference
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.terms.TermsService
import com.dogGetDrunk.meetjyou.terms.TermsType
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.sql.SQLException

class UserServiceTest : BehaviorSpec() {
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val userPreferenceRepository = mockk<UserPreferenceRepository>(relaxed = true)

    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val termsService = mockk<TermsService>(relaxed = true)
    private val userSessionRevoker = mockk<UserSessionRevoker>(relaxed = true)

    private val sut = UserService(
        userRepository,
        preferenceRepository,
        userPreferenceRepository,
        currentUserProvider,
        termsService,
        userSessionRevoker,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        // ── createUser ───────────────────────────────────────────────────────

        given("createUser 호출 시") {
            val principal = SocialPrincipal(AuthProvider.GOOGLE, subject = "google-sub-1", email = "verified@gmail.com")
            val request = RegistrationRequest(
                nickname = "newbie",
                bio = null,
                gender = Gender.M,
                age = Age.TWENTY,
                personalities = emptyList(),
                travelStyles = emptyList(),
                diet = emptyList(),
                etc = emptyList(),
                authProvider = AuthProvider.GOOGLE,
                idToken = "id-token-value",
                agreedTermsUuids = emptyList(),
            )

            `when`("정상적으로 호출되면") {
                then("검증된 principal의 식별자로 소셜 계정을 유저에 달아 함께 저장한다") {
                    every { userRepository.saveAndFlush(any<User>()) } answers { firstArg() }
                    every { preferenceRepository.findAllByTypeAndNameIn(any(), any()) } answers {
                        secondArg<List<String>>().map { name -> Preference(type = firstArg(), name = name) }
                    }
                    every { userPreferenceRepository.save(any()) } answers { firstArg() }

                    val created = sut.createUser(request, principal)

                    val account = created.socialAccounts.single()
                    account.user shouldBe created
                    account.provider shouldBe AuthProvider.GOOGLE
                    account.externalId shouldBe "google-sub-1"
                    account.email shouldBe "verified@gmail.com"
                    verify(exactly = 1) { termsService.saveUserTerms(created, any()) }
                }
            }

            `when`("동시 가입에 져서 닉네임 unique 위반이 나면") {
                then("500이 될 원본 예외 대신 DuplicateNicknameException을 던진다") {
                    every { userRepository.saveAndFlush(any<User>()) } throws uniqueViolation("user.uk_user_nickname")

                    shouldThrow<DuplicateNicknameException> { sut.createUser(request, principal) }
                }
            }

            `when`("동시 가입에 져서 같은 소셜 계정의 unique 위반이 나면") {
                then("UserAlreadyExistsException을 던진다") {
                    every { userRepository.saveAndFlush(any<User>()) } throws
                        uniqueViolation("user.uk_user_external_id")

                    shouldThrow<UserAlreadyExistsException> { sut.createUser(request, principal) }
                }
            }

            `when`("매핑하지 않은 제약 위반이 나면") {
                then("버그를 409로 숨기지 않도록 원본 예외를 그대로 던진다") {
                    every { userRepository.saveAndFlush(any<User>()) } throws
                        uniqueViolation("user.some_other_constraint")

                    shouldThrow<DataIntegrityViolationException> { sut.createUser(request, principal) }
                }
            }
        }

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
                UserPreference(user, Preference(type = PreferenceType.PERSONALITY, name = "INTROVERTED")),
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

            `when`("유저에게 personality가 하나도 없으면") {
                then("PreferenceNotFoundException을 던진다") {
                    val user = UserFixtures.user()
                    val prefsWithoutPersonality = listOf(
                        UserPreference(user, Preference(type = PreferenceType.GENDER, name = "SOME_VALUE")),
                        UserPreference(user, Preference(type = PreferenceType.AGE, name = "SOME_VALUE")),
                    )

                    every { userRepository.findByUuid(user.uuid) } returns user
                    every {
                        userPreferenceRepository.findAllByUser_IdIn(listOf(user.id))
                    } returns prefsWithoutPersonality

                    shouldThrow<PreferenceNotFoundException> { sut.getUserProfile(user.uuid) }
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
                then("하드 삭제 대신 status를 DELETED로 바꾸고 모든 기기의 토큰(리프레시·푸시)을 끊는다") {
                    sut.withdrawUser()

                    user.status shouldBe UserStatus.DELETED
                    user.withdrawnAt shouldNotBe null
                    verify { userSessionRevoker.revokeAll(user) }
                    verify(exactly = 0) { userRepository.delete(any()) }
                }
            }

            `when`("유저가 존재하지 않으면") {
                then("UserNotFoundException을 던진다") {
                    every { userRepository.findByUuid(user.uuid) } returns null

                    shouldThrow<UserNotFoundException> { sut.withdrawUser() }
                }
            }
        }

        // ── isDuplicateNickname ─────────────────────────────────────────────
        // Must agree with what sign-up / update enforce (the raw UNIQUE column): a "available"
        // answer the next request then rejects with a 409 is the bug this guards.

        given("isDuplicateNickname 호출 시") {
            `when`("닉네임을 가진 행이 있으면(탈퇴 유예가 끝났지만 purge 전인 계정 포함)") {
                then("가입·변경과 같은 기준으로 중복(true)이라고 답한다") {
                    every { userRepository.existsByNickname("nickname") } returns true

                    sut.isDuplicateNickname("nickname") shouldBe true
                }
            }

            `when`("닉네임을 가진 행이 없으면") {
                then("사용 가능(false)이라고 답한다") {
                    every { userRepository.existsByNickname("nickname") } returns false

                    sut.isDuplicateNickname("nickname") shouldBe false
                }
            }
        }

        given("updateUser 닉네임 변경 시") {
            val user = UserFixtures.user()
            val request = UserUpdateRequest(
                nickname = "taken",
                bio = null,
                gender = Gender.M,
                age = Age.TWENTY,
                personalities = emptyList(),
                travelStyles = emptyList(),
                diet = emptyList(),
                etc = emptyList(),
            )

            `when`("다른 계정이 이미 쓰는 닉네임으로 바꾸면") {
                then("DuplicateNicknameException을 던지고 닉네임은 변경되지 않는다") {
                    val originalNickname = user.nickname
                    every { currentUserProvider.user } returns user
                    every { userRepository.existsByNickname("taken") } returns true

                    shouldThrow<DuplicateNicknameException> {
                        sut.updateUser(request)
                    }
                    user.nickname shouldBe originalNickname
                    verify(exactly = 0) { userRepository.save(any()) }
                }
            }

            `when`("중복 검사는 통과했지만 동시에 같은 닉네임을 가져간 요청에 지면") {
                then("flush 시점의 unique 위반을 DuplicateNicknameException으로 바꿔 던진다") {
                    every { currentUserProvider.user } returns user
                    every { userRepository.existsByNickname("taken") } returns false
                    every { userRepository.flush() } throws uniqueViolation("user.uk_user_nickname")

                    shouldThrow<DuplicateNicknameException> {
                        sut.updateUser(request)
                    }
                }
            }

            `when`("자기 자신의 기존 닉네임을 그대로 보내면") {
                then("중복 검사를 건너뛴다") {
                    every { currentUserProvider.user } returns user
                    runCatching { sut.updateUser(request.copy(nickname = user.nickname)) }

                    verify(exactly = 0) { userRepository.existsByNickname(any()) }
                    verify(exactly = 0) { userRepository.flush() }
                }
            }
        }

        // ── saveUserPreference(s) / updateUserPreference(s) ─────────────────────
        // enum 값은 유효한데 preference 테이블에 매칭되는 row가 없는 경우 (시드 데이터 drift):
        // 조용히 드롭하지 않고 IllegalStateException을 던져야 한다 (-> 500 + Discord 알림).

        given("saveUserPreference 호출 시") {
            val user = UserFixtures.user()

            `when`("DB에 매칭되는 preference row가 있으면") {
                then("정상 저장된다") {
                    val preference = Preference(type = PreferenceType.GENDER, name = "M")
                    every {
                        preferenceRepository.findAllByTypeAndNameIn(PreferenceType.GENDER, listOf("M"))
                    } returns listOf(preference)
                    every { userPreferenceRepository.save(any()) } returns UserPreference(user, preference)

                    sut.saveUserPreference(user, "M", PreferenceType.GENDER)

                    verify(exactly = 1) { userPreferenceRepository.save(any()) }
                }
            }

            `when`("DB에 매칭되는 preference row가 없으면") {
                then("IllegalStateException을 던지고 저장하지 않는다") {
                    every {
                        preferenceRepository.findAllByTypeAndNameIn(PreferenceType.GENDER, listOf("M"))
                    } returns emptyList()

                    shouldThrow<IllegalStateException> {
                        sut.saveUserPreference(user, "M", PreferenceType.GENDER)
                    }
                    verify(exactly = 0) { userPreferenceRepository.save(any()) }
                }
            }
        }

        given("saveUserPreferences 호출 시") {
            val user = UserFixtures.user()

            `when`("요청한 이름 중 일부만 DB에 매칭되면") {
                then("IllegalStateException을 던지고 아무것도 저장하지 않는다") {
                    val matched = Preference(type = PreferenceType.PERSONALITY, name = "INTROVERTED")
                    every {
                        preferenceRepository.findAllByTypeAndNameIn(
                            PreferenceType.PERSONALITY,
                            listOf("INTROVERTED", "UNKNOWN")
                        )
                    } returns listOf(matched)

                    shouldThrow<IllegalStateException> {
                        sut.saveUserPreferences(user, listOf("INTROVERTED", "UNKNOWN"), PreferenceType.PERSONALITY)
                    }
                    verify(exactly = 0) { userPreferenceRepository.saveAll(any<List<UserPreference>>()) }
                }
            }

            `when`("요청한 이름이 모두 DB에 매칭되면") {
                then("전부 저장된다") {
                    val matched = listOf(
                        Preference(type = PreferenceType.PERSONALITY, name = "INTROVERTED"),
                        Preference(type = PreferenceType.PERSONALITY, name = "BOLD"),
                    )
                    every {
                        preferenceRepository.findAllByTypeAndNameIn(
                            PreferenceType.PERSONALITY,
                            listOf("INTROVERTED", "BOLD")
                        )
                    } returns matched

                    sut.saveUserPreferences(user, listOf("INTROVERTED", "BOLD"), PreferenceType.PERSONALITY)

                    verify(exactly = 1) { userPreferenceRepository.saveAll(match<List<UserPreference>> { it.size == 2 }) }
                }
            }
        }

        given("updateUserPreference 호출 시") {
            val user = UserFixtures.user()

            `when`("DB에 매칭되는 preference row가 없으면") {
                then("IllegalStateException을 던지고 기존 값을 지우지 않는다") {
                    every {
                        preferenceRepository.findAllByTypeAndNameIn(PreferenceType.AGE, listOf("TWENTY"))
                    } returns emptyList()

                    shouldThrow<IllegalStateException> {
                        sut.updateUserPreference(user, "TWENTY", PreferenceType.AGE)
                    }
                    verify(exactly = 0) { userPreferenceRepository.deleteByUserIdAndType(user.id, PreferenceType.AGE) }
                }
            }
        }
    }

    private fun uniqueViolation(constraintName: String) = DataIntegrityViolationException(
        "could not execute statement",
        ConstraintViolationException("Duplicate entry", SQLException("Duplicate entry"), constraintName),
    )
}
