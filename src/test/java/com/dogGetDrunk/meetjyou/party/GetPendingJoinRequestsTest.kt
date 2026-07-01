package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.dto.JoinRequestStatus
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class GetPendingJoinRequestsTest : BehaviorSpec() {

    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val chatParticipantService = mockk<ChatParticipantService>(relaxed = true)
    private val chatRoomEventBroadcaster = mockk<ChatRoomEventBroadcaster>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val sut = PartyService(
        partyRepository, postRepository, planRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("getPendingJoinRequests 호출 시") {
            val host = UserFixtures.user()
            val applicant = UserFixtures.user(email = "app@test.com", nickname = "applicant", externalId = "ext2")
            val party = NotificationCenterFixtures.party()
            val post = NotificationCenterFixtures.post(party, host)

            `when`("PENDING 신청이 존재하는 경우") {
                then("hasProfileImage, applicationNote, postUuid가 포함된 응답을 반환한다") {
                    val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)
                    val pending = NotificationCenterFixtures.pendingUserParty(party, applicant, "Hi!")
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
                    every { userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(party.uuid, MemberStatus.PENDING) } returns listOf(pending)
                    every { postRepository.findByParty_Uuid(party.uuid) } returns post

                    val result = sut.getPendingJoinRequests(party.uuid, host.uuid)

                    result.postUuid shouldBe post.uuid
                    result.requests.size shouldBe 1
                    result.requests[0].applicationNote shouldBe "Hi!"
                    result.requests[0].hasProfileImage shouldBe applicant.hasProfileImage
                }
            }
        }

        given("getMyApplications 호출 시") {
            val user = UserFixtures.user()
            val party = NotificationCenterFixtures.party()
            val post = NotificationCenterFixtures.post(party, user)

            `when`("PENDING, 수락, 거절 신청이 섞여 있는 경우") {
                then("각각 올바른 JoinRequestStatus로 매핑된다") {
                    val pending = NotificationCenterFixtures.pendingUserParty(party, user, "note")
                    val party2 = NotificationCenterFixtures.party("Party 2")
                    val post2 = NotificationCenterFixtures.post(party2, user)
                    val accepted = NotificationCenterFixtures.pendingUserParty(party2, user)
                    accepted.approve()

                    val pageable = Pageable.ofSize(20)
                    every { userPartyRepository.findAllSentApplicationsByUserUuid(user.uuid, pageable) } returns PageImpl(listOf(pending, accepted))
                    every { postRepository.findAllByParty_UuidIn(listOf(party.uuid, party2.uuid)) } returns listOf(post, post2)

                    val result = sut.getMyApplications(user.uuid, pageable)

                    result.content.size shouldBe 2
                    result.content[0].status shouldBe JoinRequestStatus.PENDING
                    result.content[0].applicationNote shouldBe "note"
                    result.content[1].status shouldBe JoinRequestStatus.ACCEPTED
                }
            }
        }
    }
}
