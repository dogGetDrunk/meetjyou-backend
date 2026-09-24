package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyNameRequest
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class UpdatePartyNameTest : BehaviorSpec() {

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

    init {
        // No clearAllMocks(): InstancePerLeaf already builds fresh mocks per leaf, and clearing in
        // beforeEach would wipe the stubs registered in the `when` blocks below.
        given("updatePartyName으로 파티 이름을 변경할 때") {
            val host = UserFixtures.user()
            val party = NotificationCenterFixtures.party(name = "Old name")
            val plan = PlanFixtures.plan(owner = host)
            party.plan = plan

            beforeEach {
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { partyRepository.findByUuid(party.uuid) } returns party
            }

            `when`("HOST가 요청하면") {
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns
                    NotificationCenterFixtures.hostUserParty(party, host)

                then("이름만 바뀌고 일정·인원·목적지·연결된 계획서는 그대로다") {
                    val itinStart = party.itinStart
                    val itinFinish = party.itinFinish
                    val capacity = party.capacity
                    val destination = party.destination

                    val response = sut.updatePartyName(party.uuid, UpdatePartyNameRequest("New name"))

                    response.name shouldBe "New name"
                    party.name shouldBe "New name"
                    party.itinStart shouldBe itinStart
                    party.itinFinish shouldBe itinFinish
                    party.capacity shouldBe capacity
                    party.destination shouldBe destination
                    party.plan shouldBe plan
                    verify(exactly = 1) { partyRepository.findByUuidForUpdate(party.uuid) }
                    // The locked read must come first: if verifyPartyHost's unlocked findByUuid ran
                    // earlier, the stale entity would stay in the persistence context and its full-column
                    // flush could revert a concurrent `joined` change.
                    verifyOrder {
                        partyRepository.findByUuidForUpdate(party.uuid)
                        partyRepository.findByUuid(party.uuid)
                    }
                }
            }

            `when`("HOST가 아닌 MEMBER가 요청하면") {
                val member = UserFixtures.user(
                    email = "member@test.com",
                    nickname = "member",
                    externalId = "ext-member",
                )
                every { currentUserProvider.uuid } returns member.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, member.uuid) } returns
                    UserParty(party, member, PartyRole.MEMBER)

                then("PartyUpdateAccessDeniedException을 던지고 이름은 그대로다") {
                    shouldThrow<PartyUpdateAccessDeniedException> {
                        sut.updatePartyName(party.uuid, UpdatePartyNameRequest("Hijacked"))
                    }
                    party.name shouldBe "Old name"
                }
            }

            `when`("파티가 이미 종료(COMPLETED)되었으면") {
                party.complete()
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns
                    NotificationCenterFixtures.hostUserParty(party, host)

                then("HOST여도 PartyUpdateAccessDeniedException을 던진다") {
                    shouldThrow<PartyUpdateAccessDeniedException> {
                        sut.updatePartyName(party.uuid, UpdatePartyNameRequest("New name"))
                    }
                    party.name shouldBe "Old name"
                }
            }
        }

        given("존재하지 않는 파티의 이름을 변경하면") {
            val missingUuid = UUID.randomUUID()
            every { currentUserProvider.uuid } returns UUID.randomUUID()
            every { partyRepository.findByUuidForUpdate(missingUuid) } returns null

            then("PartyNotFoundException을 던진다") {
                shouldThrow<PartyNotFoundException> {
                    sut.updatePartyName(missingUuid, UpdatePartyNameRequest("New name"))
                }
            }
        }
    }
}
