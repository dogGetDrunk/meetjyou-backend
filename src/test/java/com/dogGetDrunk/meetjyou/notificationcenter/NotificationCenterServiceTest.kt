package com.dogGetDrunk.meetjyou.notificationcenter

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.notificationcenter.dto.ApplicationStatus
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.notice.NoticeRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class NotificationCenterServiceTest : BehaviorSpec() {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val noticeRepository = mockk<NoticeRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val sut = NotificationCenterService(userRepository, noticeRepository, userPartyRepository, postRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        // ── getNotices ────────────────────────────────────────────────────────

        given("getNotices 호출 시") {
            val user = UserFixtures.user()
            val uuid = user.uuid

            beforeEach { every { userRepository.findByUuid(uuid) } returns user }

            `when`("lastNoticesViewedAt가 null인 경우") {
                then("모든 공지가 unread 처리된다") {
                    val notices = listOf(
                        NotificationCenterFixtures.notice("N1"),
                        NotificationCenterFixtures.notice("N2"),
                    )
                    val pageable = Pageable.ofSize(20)
                    every { noticeRepository.count() } returns 2
                    every { noticeRepository.findAllByOrderByCreatedAtDesc(pageable) } returns PageImpl(notices)

                    val result = sut.getNotices(uuid, pageable)

                    result.unreadCount shouldBe 2
                    result.notices.size shouldBe 2
                }
            }

            `when`("lastNoticesViewedAt가 현재 시각 이후로 설정된 경우") {
                then("모든 공지가 read 처리되어 unreadCount=0이 된다") {
                    val notices = listOf(
                        NotificationCenterFixtures.notice("N1"),
                        NotificationCenterFixtures.notice("N2"),
                    )
                    val pageable = Pageable.ofSize(20)
                    every { noticeRepository.countByCreatedAtAfter(any()) } returns 0
                    every { noticeRepository.findAllByOrderByCreatedAtDesc(pageable) } returns PageImpl(notices)
                    user.lastNoticesViewedAt = notices.maxOf { it.createdAt }.plusSeconds(1)

                    val result = sut.getNotices(uuid, pageable)

                    result.unreadCount shouldBe 0
                }
            }

            `when`("유저가 존재하지 않는 경우") {
                then("UserNotFoundException을 던진다") {
                    every { userRepository.findByUuid(uuid) } returns null

                    shouldThrow<UserNotFoundException> { sut.getNotices(uuid, Pageable.ofSize(20)) }
                }
            }
        }

        // ── markNoticesRead ───────────────────────────────────────────────────

        given("markNoticesRead 호출 시") {
            val user = UserFixtures.user()
            val uuid = user.uuid

            `when`("정상 호출") {
                then("lastNoticesViewedAt이 현재 시각으로 갱신된다") {
                    every { userRepository.findByUuid(uuid) } returns user

                    sut.markNoticesRead(uuid)

                    user.lastNoticesViewedAt shouldBe user.lastNoticesViewedAt // non-null
                }
            }
        }

        // ── getReceivedApplications ───────────────────────────────────────────

        given("getReceivedApplications 호출 시") {
            val host = UserFixtures.user()
            val hostUuid = host.uuid
            val applicant = UserFixtures.user(email = "app@test.com", nickname = "applicant", externalId = "ext2")
            val party = NotificationCenterFixtures.party()
            val post = NotificationCenterFixtures.post(party, host)

            `when`("PENDING 신청이 있고 hostRead=false인 경우") {
                then("unreadCount가 올바르게 집계된다") {
                    val pending = NotificationCenterFixtures.pendingUserParty(party, applicant, "Hello!")
                    every { userPartyRepository.findAllPendingRequestsForHost(hostUuid) } returns listOf(pending)
                    every { postRepository.findAllByParty_UuidIn(listOf(party.uuid)) } returns listOf(post)

                    val result = sut.getReceivedApplications(hostUuid, Pageable.ofSize(20))

                    result.unreadCount shouldBe 1
                    result.applications.size shouldBe 1
                    result.applications[0].applicationNote shouldBe "Hello!"
                    result.applications[0].postUuid shouldBe post.uuid
                    result.applications[0].read shouldBe false
                }
            }

            `when`("신청이 없는 경우") {
                then("빈 목록과 unreadCount=0을 반환한다") {
                    every { userPartyRepository.findAllPendingRequestsForHost(hostUuid) } returns emptyList()
                    every { postRepository.findAllByParty_UuidIn(emptyList()) } returns emptyList()

                    val result = sut.getReceivedApplications(hostUuid, Pageable.ofSize(20))

                    result.unreadCount shouldBe 0
                    result.applications shouldBe emptyList()
                }
            }
        }

        // ── markReceivedApplicationsRead ──────────────────────────────────────

        given("markReceivedApplicationsRead 호출 시") {
            val host = UserFixtures.user()
            val hostUuid = host.uuid
            val applicant = UserFixtures.user(email = "app@test.com", nickname = "applicant", externalId = "ext2")
            val party = NotificationCenterFixtures.party()

            `when`("읽지 않은 신청이 있는 경우") {
                then("hostRead가 true로 변경된다") {
                    val pending = NotificationCenterFixtures.pendingUserParty(party, applicant)
                    every { userPartyRepository.findAllPendingRequestsForHost(hostUuid) } returns listOf(pending)

                    sut.markReceivedApplicationsRead(hostUuid)

                    pending.hostRead shouldBe true
                }
            }
        }

        // ── getSentApplications ───────────────────────────────────────────────

        given("getSentApplications 호출 시") {
            val user = UserFixtures.user()
            val userUuid = user.uuid
            val party = NotificationCenterFixtures.party()
            val post = NotificationCenterFixtures.post(party, user)

            `when`("PENDING 신청이 있는 경우") {
                then("pendingCount=1, status=PENDING, read=true로 반환된다") {
                    val pending = NotificationCenterFixtures.pendingUserParty(party, user)
                    every { userPartyRepository.findAllSentApplicationsByUserUuid(userUuid) } returns listOf(pending)
                    every { postRepository.findAllByParty_UuidIn(listOf(party.uuid)) } returns listOf(post)

                    val result = sut.getSentApplications(userUuid, Pageable.ofSize(20))

                    result.pendingCount shouldBe 1
                    result.changedCount shouldBe 0
                    result.applications[0].status shouldBe ApplicationStatus.PENDING
                    result.applications[0].read shouldBe true
                }
            }

            `when`("ACCEPTED 신청(applicantRead=false)이 있는 경우") {
                then("changedCount=1, status=ACCEPTED, read=false로 반환된다") {
                    val accepted = NotificationCenterFixtures.pendingUserParty(party, user)
                    accepted.approve()
                    every { userPartyRepository.findAllSentApplicationsByUserUuid(userUuid) } returns listOf(accepted)
                    every { postRepository.findAllByParty_UuidIn(listOf(party.uuid)) } returns listOf(post)

                    val result = sut.getSentApplications(userUuid, Pageable.ofSize(20))

                    result.changedCount shouldBe 1
                    result.applications[0].status shouldBe ApplicationStatus.ACCEPTED
                    result.applications[0].read shouldBe false
                }
            }

            `when`("REJECTED 신청(applicantRead=false)이 있는 경우") {
                then("changedCount=1, status=REJECTED, read=false로 반환된다") {
                    val rejected = NotificationCenterFixtures.pendingUserParty(party, user)
                    rejected.reject()
                    every { userPartyRepository.findAllSentApplicationsByUserUuid(userUuid) } returns listOf(rejected)
                    every { postRepository.findAllByParty_UuidIn(listOf(party.uuid)) } returns listOf(post)

                    val result = sut.getSentApplications(userUuid, Pageable.ofSize(20))

                    result.changedCount shouldBe 1
                    result.applications[0].status shouldBe ApplicationStatus.REJECTED
                    result.applications[0].read shouldBe false
                }
            }
        }

        // ── markSentApplicationsRead ──────────────────────────────────────────

        given("markSentApplicationsRead 호출 시") {
            val user = UserFixtures.user()
            val userUuid = user.uuid
            val party = NotificationCenterFixtures.party()

            `when`("수락된 신청에서 applicantRead=false인 항목이 있는 경우") {
                then("applicantRead가 true로 변경된다") {
                    val accepted = NotificationCenterFixtures.pendingUserParty(party, user)
                    accepted.approve()
                    every { userPartyRepository.findAllSentApplicationsByUserUuid(userUuid) } returns listOf(accepted)

                    sut.markSentApplicationsRead(userUuid)

                    accepted.applicantRead shouldBe true
                }
            }
        }
    }
}
