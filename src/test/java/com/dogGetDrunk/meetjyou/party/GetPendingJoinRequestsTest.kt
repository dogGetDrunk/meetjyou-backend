package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.place.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.dto.JoinRequestStatus
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.dto.UserDisplay
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val chatParticipantService = mockk<ChatParticipantService>(relaxed = true)
    private val chatRoomEventBroadcaster = mockk<ChatRoomEventBroadcaster>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val partyImgService = mockk<PartyImgService>(relaxed = true)
    private val postImgService = mockk<PostImgService>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = PartyService(
        partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher, partyImgService, postImgService, objectMapper, currentUserProvider,
            PlaceTestDoubles.regionCatalog(),
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
                    every { currentUserProvider.uuid } returns host.uuid
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
                    every { userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(party.uuid, MemberStatus.PENDING) } returns listOf(pending)
                    every { postRepository.findByParty_Uuid(party.uuid) } returns post

                    val result = sut.getPendingJoinRequests(party.uuid)

                    result.postUuid shouldBe post.uuid
                    result.requests.size shouldBe 1
                    result.requests[0].applicationNote shouldBe "Hi!"
                    result.requests[0].hasProfileImage shouldBe applicant.hasProfileImage
                }
            }

            // AT-005 (unit): purged applicants are masked, applicants in the grace period are not.
            `when`("신청자 중 탈퇴 유예 중인 회원과 완전 탈퇴한 회원이 있는 경우") {
                then("완전 탈퇴한 신청자만 탈퇴 문구로 가리고 사진을 숨긴다") {
                    val graced = UserFixtures.gracedUser(4)
                    val purged = UserFixtures.purgedUser(5)
                    val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)
                    every { currentUserProvider.uuid } returns host.uuid
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
                    every {
                        userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(party.uuid, MemberStatus.PENDING)
                    } returns listOf(
                        NotificationCenterFixtures.pendingUserParty(party, applicant, "Hi!"),
                        NotificationCenterFixtures.pendingUserParty(party, graced, "Hi!"),
                        NotificationCenterFixtures.pendingUserParty(party, purged, "Hi!"),
                    )
                    every { postRepository.findByParty_Uuid(party.uuid) } returns post

                    val byUuid = sut.getPendingJoinRequests(party.uuid).requests.associateBy { it.userUuid }

                    byUuid.getValue(applicant.uuid).nickname shouldBe "applicant"
                    byUuid.getValue(applicant.uuid).isWithdrawn shouldBe false
                    byUuid.getValue(graced.uuid).nickname shouldBe graced.nickname
                    byUuid.getValue(graced.uuid).hasProfileImage shouldBe true
                    byUuid.getValue(graced.uuid).isWithdrawn shouldBe false
                    byUuid.getValue(purged.uuid).nickname shouldBe UserDisplay.WITHDRAWN_NICKNAME
                    byUuid.getValue(purged.uuid).hasProfileImage shouldBe false
                    byUuid.getValue(purged.uuid).isWithdrawn shouldBe true
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
                    every { currentUserProvider.uuid } returns user.uuid
                    every { userPartyRepository.findAllSentApplicationsByUserUuid(user.uuid, pageable) } returns PageImpl(listOf(pending, accepted))
                    every { postRepository.findAllByParty_UuidIn(listOf(party.uuid, party2.uuid)) } returns listOf(post, post2)

                    val result = sut.getMyApplications(pageable)

                    result.content.size shouldBe 2
                    result.content[0].status shouldBe JoinRequestStatus.PENDING
                    result.content[0].applicationNote shouldBe "note"
                    result.content[1].status shouldBe JoinRequestStatus.ACCEPTED
                }
            }
        }
    }
}
