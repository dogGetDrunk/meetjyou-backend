package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyMemberAccessDeniedException
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
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
    private val partyImgService = mockk<PartyImgService>(relaxed = true)
    private val postImgService = mockk<PostImgService>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val sut = PartyService(
        partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher, partyImgService, postImgService, objectMapper,
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
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, member.uuid) } returns memberMembership
                    every {
                        userPartyRepository.findAllWithUserByPartyUuidAndMemberStatus(party.uuid, MemberStatus.JOINED)
                    } returns listOf(hostMembership, memberMembership)

                    val result = sut.getPartyMembers(party.uuid, member.uuid)

                    result.size shouldBe 2
                    result.map { it.userUuid } shouldBe listOf(host.uuid, member.uuid)
                    result.first { it.userUuid == host.uuid }.role shouldBe PartyRole.HOST
                }
            }

            `when`("요청자가 파티 멤버가 아닌 경우") {
                then("PartyMemberAccessDeniedException을 던진다") {
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, outsider.uuid) } returns null

                    shouldThrow<PartyMemberAccessDeniedException> {
                        sut.getPartyMembers(party.uuid, outsider.uuid)
                    }
                }
            }

            `when`("요청자가 BANNED 상태인 경우") {
                then("PartyMemberAccessDeniedException을 던진다") {
                    val bannedMembership = UserParty(party, outsider, PartyRole.MEMBER).also { it.ban() }
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, outsider.uuid) } returns bannedMembership

                    shouldThrow<PartyMemberAccessDeniedException> {
                        sut.getPartyMembers(party.uuid, outsider.uuid)
                    }
                }
            }
        }
    }
}
