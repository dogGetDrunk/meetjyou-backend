package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinCancelNotAllowedException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyJoinRequestNotFoundException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class CancelJoinRequestTest : BehaviorSpec() {

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

        given("cancelJoinRequest 호출 시") {
            val user = UserFixtures.user()
            val party = NotificationCenterFixtures.party()
            val partyUuid = party.uuid
            val userUuid = user.uuid

            beforeEach { every { currentUserProvider.uuid } returns userUuid }

            `when`("PENDING 상태인 신청이 존재하는 경우") {
                then("레코드가 삭제된다") {
                    val membership = NotificationCenterFixtures.pendingUserParty(party, user)
                    every { userPartyRepository.lockByPartyUuidAndUserUuid(partyUuid, userUuid) } returns membership
                    every { userPartyRepository.deleteByParty_UuidAndUser_Uuid(partyUuid, userUuid) } returns 1

                    sut.cancelJoinRequest(partyUuid)

                    verify(exactly = 1) { userPartyRepository.deleteByParty_UuidAndUser_Uuid(partyUuid, userUuid) }
                }
            }

            `when`("신청 레코드가 존재하지 않는 경우") {
                then("PartyJoinRequestNotFoundException을 던진다") {
                    every { userPartyRepository.lockByPartyUuidAndUserUuid(partyUuid, userUuid) } returns null

                    shouldThrow<PartyJoinRequestNotFoundException> {
                        sut.cancelJoinRequest(partyUuid)
                    }
                }
            }

            `when`("PENDING이 아닌 상태(JOINED)인 경우") {
                then("PartyJoinCancelNotAllowedException을 던진다") {
                    val membership = NotificationCenterFixtures.hostUserParty(party, user)
                    // hostUserParty has memberStatus=JOINED (default)
                    every { userPartyRepository.lockByPartyUuidAndUserUuid(partyUuid, userUuid) } returns membership

                    shouldThrow<PartyJoinCancelNotAllowedException> {
                        sut.cancelJoinRequest(partyUuid)
                    }
                }
            }
        }
    }
}
