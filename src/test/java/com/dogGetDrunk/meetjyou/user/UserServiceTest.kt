package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.DefaultProfileImageProvider
import com.dogGetDrunk.meetjyou.image.ImageTarget
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll

class UserServiceTest : BehaviorSpec() {
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val userPreferenceRepository = mockk<UserPreferenceRepository>(relaxed = true)

    private val defaultProfileImageProvider = mockk<DefaultProfileImageProvider>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)

    private val sut = UserService(
        userRepository,
        preferenceRepository,
        userPreferenceRepository,
        defaultProfileImageProvider,
        currentUserProvider,
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
                then("ImageTarget 기반으로 imgUrl과 thumbImgUrl이 설정된다") {
                    sut.confirmProfileImage()

                    user.imgUrl shouldBe ImageTarget.USER_PROFILE_ORIGINAL.toObjectName(uuid)
                    user.thumbImgUrl shouldBe ImageTarget.USER_PROFILE_THUMBNAIL.toObjectName(uuid)
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
                imgUrl = "image/user/profile/some-uuid-original.jpg"
                thumbImgUrl = "image/user/profile/some-uuid-thumbnail.jpg"
            }
            val uuid = user.uuid

            beforeEach {
                every { currentUserProvider.uuid } returns uuid
                every { currentUserProvider.user } returns user
            }

            `when`("정상적으로 호출되면") {
                then("imgUrl과 thumbImgUrl이 null로 초기화된다") {
                    sut.clearProfileImage()

                    user.imgUrl shouldBe null
                    user.thumbImgUrl shouldBe null
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

            `when`("consented = true로 호출되면") {
                then("marketingConsented가 true로 변경된다") {
                    sut.updateMarketingConsent(true)

                    user.marketingConsented shouldBe true
                }
            }

            `when`("consented = false로 호출되면") {
                then("marketingConsented가 false로 유지된다") {
                    user.marketingConsented = true
                    sut.updateMarketingConsent(false)

                    user.marketingConsented shouldBe false
                }
            }

            `when`("유저가 존재하지 않으면") {
                then("UserNotFoundException을 던진다") {
                    every { currentUserProvider.user } throws UserNotFoundException(uuid)

                    shouldThrow<UserNotFoundException> { sut.updateMarketingConsent(true) }
                }
            }
        }

        // ── getUserProfile (thumbImgUrl fallback) ────────────────────────────

        given("getUserProfile 호출 시") {
            `when`("유저에 thumbImgUrl이 없고 기본 이미지가 설정된 경우") {
                then("응답의 thumbImgUrl에 기본 이미지 URL이 담긴다") {
                    val user = UserFixtures.user()
                    val defaultUrl = "https://cdn.example.com/default-profile.jpg"

                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { defaultProfileImageProvider.getDefaultThumbnailUrl() } returns defaultUrl
                    every { userPreferenceRepository.findPreferenceByUserIdAndType(any(), any()) } returns
                            mockk { every { name } returns "SOME_VALUE" }
                    every { userPreferenceRepository.findPreferencesByUserIdAndType(any(), any()) } returns emptyList()

                    val result = sut.getUserProfile(user.uuid)

                    result.thumbImgUrl shouldBe defaultUrl
                }
            }

            `when`("유저에 thumbImgUrl이 있는 경우") {
                then("응답의 thumbImgUrl에 유저의 이미지 경로가 담긴다") {
                    val user = UserFixtures.user().apply {
                        thumbImgUrl = "image/user/profile/uuid-thumbnail.jpg"
                    }

                    every { userRepository.findByUuid(user.uuid) } returns user
                    every { userPreferenceRepository.findPreferenceByUserIdAndType(any(), any()) } returns
                            mockk { every { name } returns "SOME_VALUE" }
                    every { userPreferenceRepository.findPreferencesByUserIdAndType(any(), any()) } returns emptyList()

                    val result = sut.getUserProfile(user.uuid)

                    result.thumbImgUrl shouldBe "image/user/profile/uuid-thumbnail.jpg"
                }
            }
        }
    }
}
