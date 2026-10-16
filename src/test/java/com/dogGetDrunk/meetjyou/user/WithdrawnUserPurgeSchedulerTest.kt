package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.UserImgService
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

class WithdrawnUserPurgeSchedulerTest : BehaviorSpec() {

    private val purgeService = mockk<WithdrawnUserPurgeService>(relaxed = true)
    private val userImgService = mockk<UserImgService>(relaxed = true)
    private val sut = WithdrawnUserPurgeScheduler(purgeService, userImgService)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("프로필 이미지가 있는 유저와 없는 유저가 purge 대상이면") {
            val withImage = UserFixtures.user(nickname = "img").apply { hasProfileImage = true }
            val withoutImage = UserFixtures.user(nickname = "noimg")

            `when`("스케줄러가 돌면") {
                then("이미지가 있는 유저의 이미지만 먼저 지우고 DB purge를 호출한다") {
                    every { purgeService.findPurgeTargets(any()) } returns listOf(withImage, withoutImage)

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
    }
}
