package com.dogGetDrunk.meetjyou.notification.preference

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notification.NotificationType
import com.dogGetDrunk.meetjyou.notification.preference.dto.UpdateNotificationSettingsRequest
import com.dogGetDrunk.meetjyou.user.UserRepository
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

class NotificationPreferenceServiceTest : BehaviorSpec() {
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val preferenceRepository = mockk<UserNotificationPreferenceRepository>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = NotificationPreferenceService(userRepository, preferenceRepository, currentUserProvider)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }
        afterSpec { unmockkAll() }

        // ── getSettings ──────────────────────────────────────────────────────

        given("getSettings 호출 시") {
            val user = UserFixtures.user()
            val uuid = user.uuid

            beforeEach {
                every { currentUserProvider.uuid } returns uuid
                every { currentUserProvider.user } returns user
            }

            `when`("카테고리 설정이 하나도 저장되지 않은 경우") {
                then("모든 카테고리가 기본값 true로 반환된다") {
                    every { preferenceRepository.findAllByUser(user) } returns emptyList()

                    val result = sut.getSettings()

                    result.globalEnabled shouldBe true
                    NotificationType.entries.forEach { type ->
                        result.categories[type] shouldBe true
                    }
                }
            }

            `when`("일부 카테고리가 false로 저장된 경우") {
                then("저장된 값이 반영되고 나머지는 true로 반환된다") {
                    user.notified = false
                    val pref = UserNotificationPreference(user, NotificationType.CHAT_MESSAGE, enabled = false)
                    every { preferenceRepository.findAllByUser(user) } returns listOf(pref)

                    val result = sut.getSettings()

                    result.globalEnabled shouldBe false
                    result.categories[NotificationType.CHAT_MESSAGE] shouldBe false
                    result.categories[NotificationType.NOTICE] shouldBe true
                }
            }

            `when`("유저가 존재하지 않는 경우") {
                then("UserNotFoundException을 던진다") {
                    every { currentUserProvider.user } throws UserNotFoundException(uuid)

                    shouldThrow<UserNotFoundException> {
                        sut.getSettings()
                    }
                }
            }
        }

        // ── updateSettings ───────────────────────────────────────────────────

        given("updateSettings 호출 시") {
            val user = UserFixtures.user()
            val uuid = user.uuid

            beforeEach {
                every { currentUserProvider.uuid } returns uuid
                every { currentUserProvider.user } returns user
            }

            `when`("globalEnabled만 전달된 경우") {
                then("global 설정만 변경되고 카테고리 설정은 변경되지 않는다") {
                    val request = UpdateNotificationSettingsRequest(globalEnabled = false, categories = null)

                    sut.updateSettings(request)

                    user.notified shouldBe false
                    verify(exactly = 0) { preferenceRepository.findByUserAndNotificationType(any(), any()) }
                }
            }

            `when`("categories만 전달된 경우") {
                then("global 설정은 변경되지 않고 카테고리 설정만 변경된다") {
                    val existing = UserNotificationPreference(user, NotificationType.CHAT_MESSAGE, enabled = true)
                    every {
                        preferenceRepository.findByUserAndNotificationType(user, NotificationType.CHAT_MESSAGE)
                    } returns existing
                    every {
                        preferenceRepository.findByUserAndNotificationType(user, NotificationType.NOTICE)
                    } returns null
                    every { preferenceRepository.save(any()) } answers { firstArg() }

                    val request = UpdateNotificationSettingsRequest(
                        globalEnabled = null,
                        categories = mapOf(
                            NotificationType.CHAT_MESSAGE to false,
                            NotificationType.NOTICE to false,
                        )
                    )

                    sut.updateSettings(request)

                    user.notified shouldBe true
                    existing.enabled shouldBe false
                    verify(exactly = 1) { preferenceRepository.save(any()) }
                }
            }

            `when`("기존 카테고리 설정이 존재하는 경우") {
                then("새로운 row를 추가하지 않고 기존 row를 수정한다") {
                    val existing = UserNotificationPreference(user, NotificationType.NOTICE, enabled = true)
                    every {
                        preferenceRepository.findByUserAndNotificationType(user, NotificationType.NOTICE)
                    } returns existing

                    val request = UpdateNotificationSettingsRequest(
                        globalEnabled = null,
                        categories = mapOf(NotificationType.NOTICE to false)
                    )

                    sut.updateSettings(request)

                    existing.enabled shouldBe false
                    verify(exactly = 0) { preferenceRepository.save(any()) }
                }
            }

            `when`("유저가 존재하지 않는 경우") {
                then("UserNotFoundException을 던진다") {
                    every { currentUserProvider.user } throws UserNotFoundException(uuid)

                    shouldThrow<UserNotFoundException> {
                        sut.updateSettings(UpdateNotificationSettingsRequest(globalEnabled = false, categories = null))
                    }
                }
            }
        }
    }
}
