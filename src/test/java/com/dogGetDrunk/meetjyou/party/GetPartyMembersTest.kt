package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.place.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyMemberAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher

class GetPartyMembersTest : BehaviorSpec() {

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
    private val objectMapper = ObjectMapper()
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = PartyService(
        partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher, objectMapper, currentUserProvider,
            PlaceTestDoubles.regionCatalog(),
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("getPartyMembers 호출 시") {
            val host = UserFixtures.user()
            val member = UserFixtures.user(email = "member@test.com", nickname = "member", externalId = "ext2")
            val outsider = UserFixtures.user(email = "outsider@test.com", nickname = "outsider", externalId = "ext3")
            val party = NotificationCenterFixtures.party()

            `when`("요청자가 파티에 가입된 멤버인 경우") {
                then("HOST와 MEMBER를 포함한 전체 멤버 목록을 반환한다") {
                    val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)
                    val memberMembership = UserParty(party, member, PartyRole.MEMBER)
                    every { currentUserProvider.uuid } returns member.uuid
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, member.uuid) } returns memberMembership
                    every {
                        userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(party.uuid, MemberStatus.JOINED)
                    } returns listOf(hostMembership, memberMembership)

                    val result = sut.getPartyMembers(party.uuid)

                    result.size shouldBe 2
                    result.map { it.userUuid } shouldBe listOf(host.uuid, member.uuid)
                    result.first { it.userUuid == host.uuid }.role shouldBe PartyRole.HOST
                }
            }

            // AT-004 (unit): purged members are masked, members in the grace period are not.
            `when`("멤버 중 탈퇴 유예 중인 회원과 완전 탈퇴한 회원이 있는 경우") {
                then("완전 탈퇴한 회원만 탈퇴 문구로 가리고 사진을 숨긴다") {
                    val graced = UserFixtures.gracedUser(4)
                    val purged = UserFixtures.purgedUser(5)
                    val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)
                    every { currentUserProvider.uuid } returns host.uuid
                    every {
                        userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid)
                    } returns hostMembership
                    every {
                        userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(party.uuid, MemberStatus.JOINED)
                    } returns listOf(
                        hostMembership,
                        UserParty(party, graced, PartyRole.MEMBER),
                        UserParty(party, purged, PartyRole.MEMBER),
                    )

                    val byUuid = sut.getPartyMembers(party.uuid).associateBy { it.userUuid }

                    byUuid.getValue(graced.uuid).nickname shouldBe graced.nickname
                    byUuid.getValue(graced.uuid).hasProfileImage shouldBe true
                    byUuid.getValue(graced.uuid).isWithdrawn shouldBe false
                    byUuid.getValue(purged.uuid).nickname shouldBe UserDisplay.WITHDRAWN_NICKNAME
                    byUuid.getValue(purged.uuid).hasProfileImage shouldBe false
                    byUuid.getValue(purged.uuid).isWithdrawn shouldBe true
                    byUuid.getValue(host.uuid).isWithdrawn shouldBe false
                }
            }

            `when`("요청자가 파티 멤버가 아닌 경우") {
                then("PartyMemberAccessDeniedException을 던진다") {
                    every { currentUserProvider.uuid } returns outsider.uuid
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, outsider.uuid) } returns null

                    shouldThrow<PartyMemberAccessDeniedException> {
                        sut.getPartyMembers(party.uuid)
                    }
                }
            }

            `when`("요청자가 BANNED 상태인 경우") {
                then("PartyMemberAccessDeniedException을 던진다") {
                    val bannedMembership = UserParty(party, outsider, PartyRole.MEMBER).also { it.ban() }
                    every { currentUserProvider.uuid } returns outsider.uuid
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, outsider.uuid) } returns bannedMembership

                    shouldThrow<PartyMemberAccessDeniedException> {
                        sut.getPartyMembers(party.uuid)
                    }
                }
            }
        }
    }
}
