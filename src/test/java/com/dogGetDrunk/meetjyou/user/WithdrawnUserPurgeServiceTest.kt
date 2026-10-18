package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyRepository
import com.dogGetDrunk.meetjyou.notification.preference.UserNotificationPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.UserPreferenceRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.Instant

class WithdrawnUserPurgeServiceTest : BehaviorSpec() {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val userPreferenceRepository = mockk<UserPreferenceRepository>(relaxed = true)
    private val userSessionRevoker = mockk<UserSessionRevoker>(relaxed = true)
    private val notificationPreferenceRepository = mockk<UserNotificationPreferenceRepository>(relaxed = true)
    private val idempotencyKeyRepository = mockk<IdempotencyKeyRepository>(relaxed = true)
    private val sut = WithdrawnUserPurgeService(
        userRepository,
        userPreferenceRepository,
        userSessionRevoker,
        notificationPreferenceRepository,
        idempotencyKeyRepository,
    )

    private val now = Instant.parse("2026-10-14T00:00:00Z")

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("purge 대상 조회는") {
            `when`("호출되면") {
                then("현재 시각에서 유예기간을 뺀 시각을 기준으로 조회한다") {
                    sut.findPurgeTargets(now)

                    verify {
                        userRepository.findAllByStatusAndWithdrawnAtLessThanEqual(
                            UserStatus.DELETED,
                            now.minus(User.WITHDRAWAL_GRACE_PERIOD),
                        )
                    }
                }
            }
        }

        given("유예기간이 끝난 탈퇴 유저와, 그 사이 상태가 바뀐 유저가 함께 넘어오면") {
            val expired = UserFixtures.user().apply { withdraw(now.minus(Duration.ofDays(31))) }
            val restored = UserFixtures.user(nickname = "restored")

            `when`("purge하면") {
                then("만료된 유저만 익명화하고 하위 데이터를 일괄 삭제한다") {
                    every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(expired, restored)

                    val purged = sut.purge(listOf(1L, 2L), now)

                    purged shouldBe 1
                    expired.status shouldBe UserStatus.PURGED
                    restored.status shouldBe UserStatus.NORMAL
                    verify { userRepository.deleteSocialAccountsByUserIdIn(listOf(expired.id)) }
                    verify { userPreferenceRepository.deleteAllByUserIdIn(listOf(expired.id)) }
                    verify { userSessionRevoker.deleteAll(listOf(expired.id)) }
                    verify { notificationPreferenceRepository.deleteAllByUserIdIn(listOf(expired.id)) }
                    verify { idempotencyKeyRepository.deleteAllByUserIdIn(listOf(expired.id)) }
                }
            }
        }

        given("다시 확인했을 때 대상이 하나도 없으면") {
            `when`("purge하면") {
                then("아무것도 삭제하지 않는다") {
                    every { userRepository.findAllById(any<Iterable<Long>>()) } returns emptyList()

                    sut.purge(listOf(1L), now) shouldBe 0

                    verify(exactly = 0) { userRepository.deleteSocialAccountsByUserIdIn(any()) }
                    verify(exactly = 0) { userSessionRevoker.deleteAll(any()) }
                    verify(exactly = 0) { notificationPreferenceRepository.deleteAllByUserIdIn(any()) }
                    verify(exactly = 0) { idempotencyKeyRepository.deleteAllByUserIdIn(any()) }
                }
            }
        }
    }
}
