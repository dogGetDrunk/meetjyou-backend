package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.HostBanNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.HostLeaveNotAllowedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
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
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class BanMemberAndLeavePartyTest : BehaviorSpec() {

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
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("banMember 호출 시") {
            val host = UserFixtures.user()
            val target = UserFixtures.user(email = "target@test.com", nickname = "target", externalId = "ext-target")
            val party = NotificationCenterFixtures.party()
            val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)
            val targetMembership = UserParty(party, target, PartyRole.MEMBER)

            beforeEach {
                every { currentUserProvider.uuid } returns host.uuid
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, target.uuid) } returns targetMembership
            }

            `when`("정상적으로 강퇴하면") {
                then("joined 카운터가 1 감소하고 대상은 BANNED 상태가 된다") {
                    val joinedBefore = party.joined
                    sut.banMember(party.uuid, target.uuid)

                    party.joined shouldBe joinedBefore - 1
                    targetMembership.memberStatus.name shouldBe "BANNED"
                    verify(exactly = 1) { partyRepository.findByUuidForUpdate(party.uuid) }
                }
            }

            `when`("HOST를 강퇴 시도하면") {
                then("HostBanNotAllowedException을 던지고 joined는 변하지 않는다") {
                    val otherHost = UserFixtures.user(email = "host2@test.com", nickname = "host2", externalId = "ext-host2")
                    val otherHostMembership = NotificationCenterFixtures.hostUserParty(party, otherHost)
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, otherHost.uuid) } returns otherHostMembership

                    val joinedBefore = party.joined
                    shouldThrow<HostBanNotAllowedException> {
                        sut.banMember(party.uuid, otherHost.uuid)
                    }
                    party.joined shouldBe joinedBefore
                }
            }
        }

        given("leaveParty 호출 시") {
            val host = UserFixtures.user()
            val member = UserFixtures.user(email = "member@test.com", nickname = "member", externalId = "ext-member")
            val party = NotificationCenterFixtures.party()
            val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)
            val memberMembership = UserParty(party, member, PartyRole.MEMBER)

            beforeEach {
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
            }

            `when`("일반 멤버가 탈퇴하면") {
                then("joined 카운터가 1 감소한다") {
                    every { currentUserProvider.uuid } returns member.uuid
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, member.uuid) } returns memberMembership

                    val joinedBefore = party.joined
                    sut.leaveParty(party.uuid)

                    party.joined shouldBe joinedBefore - 1
                    memberMembership.memberStatus.name shouldBe "LEFT"
                    verify(exactly = 1) { partyRepository.findByUuidForUpdate(party.uuid) }
                }
            }

            `when`("HOST가 탈퇴를 시도하면") {
                then("HostLeaveNotAllowedException을 던지고 joined는 변하지 않는다") {
                    every { currentUserProvider.uuid } returns host.uuid
                    every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership

                    val joinedBefore = party.joined
                    shouldThrow<HostLeaveNotAllowedException> {
                        sut.leaveParty(party.uuid)
                    }
                    party.joined shouldBe joinedBefore
                }
            }
        }
    }
}
