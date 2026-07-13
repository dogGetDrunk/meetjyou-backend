package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyCapacityBelowJoinedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
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

class UpdatePartyCapacityTest : BehaviorSpec() {

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
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val sut = PartyService(
        partyRepository, postRepository, planRepository, markerRepository, chatRoomRepository,
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository,
        userRepository, publisher, partyImgService, postImgService, objectMapper, currentUserProvider,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun request(capacity: Int, party: Party) = UpdatePartyRequest(
        itinStart = party.itinStart,
        itinFinish = party.itinFinish,
        destination = party.destination,
        capacity = capacity,
        name = "New name",
        planUuid = null,
    )

    init {
        beforeEach { clearAllMocks() }

        given("updateParty로 capacity(최대 인원수)를 변경할 때") {
            val host = UserFixtures.user()
            val party = NotificationCenterFixtures.party().apply { joined = 3 }
            val hostMembership = NotificationCenterFixtures.hostUserParty(party, host)

            beforeEach {
                every { currentUserProvider.uuid } returns host.uuid
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership
            }

            `when`("새 capacity가 현재 참여 인원(joined)보다 크거나 같으면") {
                then("정상적으로 capacity가 변경된다") {
                    sut.updateParty(party.uuid, request(capacity = 5, party = party))

                    party.capacity shouldBe 5
                }
            }

            `when`("새 capacity가 현재 참여 인원(joined)과 같으면") {
                then("정상적으로 capacity가 변경된다") {
                    sut.updateParty(party.uuid, request(capacity = 3, party = party))

                    party.capacity shouldBe 3
                }
            }

            `when`("새 capacity가 현재 참여 인원(joined)보다 작으면") {
                then("PartyCapacityBelowJoinedException을 던지고 capacity는 변경되지 않는다") {
                    val originalCapacity = party.capacity

                    shouldThrow<PartyCapacityBelowJoinedException> {
                        sut.updateParty(party.uuid, request(capacity = 2, party = party))
                    }
                    party.capacity shouldBe originalCapacity
                }
            }
        }
    }
}
