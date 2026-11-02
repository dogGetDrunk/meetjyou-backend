package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.discord.DiscordAlertService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.UserImgService
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.test.util.ReflectionTestUtils

class WithdrawnUserPurgeSchedulerTest : BehaviorSpec() {

    private val purgeService = mockk<WithdrawnUserPurgeService>(relaxed = true)
    private val userImgService = mockk<UserImgService>(relaxed = true)
    private val discordAlertService = mockk<DiscordAlertService>(relaxed = true)
    private val sut = WithdrawnUserPurgeScheduler(purgeService, userImgService, discordAlertService)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("프로필 이미지가 있는 유저와 없는 유저가 purge 대상이면") {
            val withImage = UserFixtures.user(nickname = "img").apply { hasProfileImage = true }
            val withoutImage = UserFixtures.user(nickname = "noimg")

            `when`("스케줄러가 돌면") {
                then("이미지가 있는 유저의 이미지만 먼저 지우고 DB purge를 호출한다") {
                    every { purgeService.findPurgeTargets(any()) } returns listOf(withImage, withoutImage)
                    every { userImgService.deleteUserProfileImg(withImage.uuid) } returns true

                    sut.purgeExpiredWithdrawals()

                    verify(exactly = 0) { userImgService.deleteUserProfileImg(withoutImage.uuid) }
                    verifyOrder {
                        userImgService.deleteUserProfileImg(withImage.uuid)
                        purgeService.purge(listOf(withImage.id, withoutImage.id), any())
                    }
                }
            }
        }

        given("purge 대상이 없으면") {
            `when`("스케줄러가 돌면") {
                then("purge를 호출하지 않는다") {
                    every { purgeService.findPurgeTargets(any()) } returns emptyList()

                    sut.purgeExpiredWithdrawals()

                    verify(exactly = 0) { purgeService.purge(any(), any()) }
                }
            }
        }

        // AT-1-1
        given("이미지 삭제 실패·성공 유저와 이미지 없는 유저가 purge 대상이면") {
            val failed = imageUser("failed", 1L)
            val succeeded = imageUser("ok", 2L)
            val noImage = withId(UserFixtures.user(nickname = "noimg"), 3L)
            `when`("스케줄러가 돌면") {
                then("실패 유저만 제외하고 이미지 삭제 후 purge한다") {
                    every { purgeService.findPurgeTargets(any()) } returns listOf(failed, succeeded, noImage)
                    every { userImgService.deleteUserProfileImg(failed.uuid) } returns false
                    every { userImgService.deleteUserProfileImg(succeeded.uuid) } returns true

                    sut.purgeExpiredWithdrawals()

                    verify(exactly = 1) { purgeService.purge(listOf(succeeded.id, noImage.id), any()) }
                    verifyOrder {
                        userImgService.deleteUserProfileImg(failed.uuid)
                        userImgService.deleteUserProfileImg(succeeded.uuid)
                        purgeService.purge(any(), any())
                    }
                }
            }
        }

        // AT-1-2
        given("모든 대상의 프로필 이미지 삭제가 실패하면") {
            val first = imageUser("f1", 1L)
            val second = imageUser("f2", 2L)
            `when`("스케줄러가 돌면") {
                then("purge를 호출하지 않고 알림을 한 번 보낸다") {
                    every { purgeService.findPurgeTargets(any()) } returns listOf(first, second)
                    every { userImgService.deleteUserProfileImg(any()) } returns false

                    sut.purgeExpiredWithdrawals()

                    verify(exactly = 0) { purgeService.purge(any(), any()) }
                    verify(exactly = 1) { discordAlertService.sendOperationalAlert(any(), any(), any(), any()) }
                }
            }
        }

        // AT-2-1 A
        given("이미지 삭제 실패 유저가 2명이면") {
            val first = imageUser("f1", 1L)
            val second = imageUser("f2", 2L)
            val ok = imageUser("ok", 3L)
            `when`("스케줄러가 돌면") {
                then("실패 수와 uuid를 담은 알림을 정확히 한 번 보낸다") {
                    every { purgeService.findPurgeTargets(any()) } returns listOf(first, second, ok)
                    every { userImgService.deleteUserProfileImg(any()) } returns false
                    every { userImgService.deleteUserProfileImg(ok.uuid) } returns true
                    val summary = slot<String>()
                    val detail = slot<String>()

                    sut.purgeExpiredWithdrawals()

                    verify(exactly = 1) {
                        discordAlertService.sendOperationalAlert(any(), any(), capture(summary), capture(detail))
                    }
                    summary.captured shouldContain "2"
                    val message = "${summary.captured} ${detail.captured}"
                    message shouldContain first.uuid.toString()
                    message shouldContain second.uuid.toString()
                    message shouldNotContain ok.uuid.toString()
                }
            }
        }

        // AT-2-1 B
        given("이미지 삭제가 모두 성공하면") {
            val user = UserFixtures.user(nickname = "ok").apply { hasProfileImage = true }
            `when`("스케줄러가 돌면") {
                then("알림을 보내지 않는다") {
                    every { purgeService.findPurgeTargets(any()) } returns listOf(user)
                    every { userImgService.deleteUserProfileImg(user.uuid) } returns true

                    sut.purgeExpiredWithdrawals()

                    verify(exactly = 0) { discordAlertService.sendOperationalAlert(any(), any(), any(), any()) }
                }
            }
        }
    }

    // UserFixtures leaves id at 0; the scheduler excludes failures by id, so each user needs its own.
    private fun withId(user: User, id: Long): User {
        ReflectionTestUtils.setField(user, "id", id)
        return user
    }

    private fun imageUser(nickname: String, id: Long): User {
        val user = withId(UserFixtures.user(nickname = nickname), id)
        user.hasProfileImage = true
        return user
    }
}
