package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PartyImgService
import com.dogGetDrunk.meetjyou.image.cloud.oracle.service.PostImgService
import com.dogGetDrunk.meetjyou.party.dto.UpdatePartyRequest
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
import com.dogGetDrunk.meetjyou.post.Post
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
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

class PartyImageAndPlanTest : BehaviorSpec() {

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

    private fun party() = Party(
        itinStart = Instant.parse("2026-05-01T00:00:00Z"),
        itinFinish = Instant.parse("2026-05-05T00:00:00Z"),
        destination = "Seoul",
        joined = 1,
        capacity = 4,
        name = "Trip",
    )

    private fun hostMembership(party: Party, host: com.dogGetDrunk.meetjyou.user.User) =
        UserParty(party, host, PartyRole.HOST)

    private fun post(party: Party, author: com.dogGetDrunk.meetjyou.user.User) = Post(
        party = party,
        isInstant = false,
        title = "Trip post",
        content = "content",
        itinStart = party.itinStart,
        itinFinish = party.itinFinish,
        location = party.destination,
        capacity = party.capacity,
    ).apply { this.author = author }

    init {
        given("파티 수정 시 planUuid가 함께 전달되면") {
            `when`("호출한 유저가 파티 호스트이고 여행 계획서 소유자인 경우") {
                val host = UserFixtures.user()
                val party = party()
                val plan = PlanFixtures.plan(owner = host)
                val request = UpdatePartyRequest(
                    itinStart = party.itinStart,
                    itinFinish = party.itinFinish,
                    destination = party.destination,
                    capacity = party.capacity,
                    name = "New name",
                    planUuid = plan.uuid,
                )
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership(party, host)
                every { planRepository.findByUuid(plan.uuid) } returns plan

                then("파티의 plan이 갱신된다") {
                    sut.updateParty(party.uuid, request)
                    party.plan shouldBe plan
                }
            }

            `when`("여행 계획서 소유자가 아닌 경우") {
                val host = UserFixtures.user()
                val party = party()
                val stranger = UserFixtures.user(email = "stranger@test.com", nickname = "stranger", externalId = "ext-stranger")
                val strangerPlan = PlanFixtures.plan(owner = stranger)
                val request = UpdatePartyRequest(
                    itinStart = party.itinStart,
                    itinFinish = party.itinFinish,
                    destination = party.destination,
                    capacity = party.capacity,
                    name = "New name",
                    planUuid = strangerPlan.uuid,
                )
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership(party, host)
                every { planRepository.findByUuid(strangerPlan.uuid) } returns strangerPlan

                then("PlanUpdateAccessDeniedException이 발생한다") {
                    shouldThrow<PlanUpdateAccessDeniedException> {
                        sut.updateParty(party.uuid, request)
                    }
                }
            }
        }

        given("파티 수정 시 planUuid가 null로 전달되면") {
            `when`("연결된 모집글이 있으면") {
                val host = UserFixtures.user()
                val party = party()
                val plan = PlanFixtures.plan(owner = host)
                party.plan = plan
                val linkedPost = post(party, host).apply { this.plan = plan; this.isPlanPublic = true }
                val request = UpdatePartyRequest(
                    itinStart = party.itinStart,
                    itinFinish = party.itinFinish,
                    destination = party.destination,
                    capacity = party.capacity,
                    name = "New name",
                    planUuid = null,
                )
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership(party, host)
                every { postRepository.findByParty_Uuid(party.uuid) } returns linkedPost

                then("파티와 연결된 모집글의 plan이 함께 해제된다") {
                    sut.updateParty(party.uuid, request)
                    party.plan shouldBe null
                    linkedPost.plan shouldBe null
                    linkedPost.isPlanPublic shouldBe null
                }
            }
        }

        given("파티 수정 시 다른 planUuid로 교체되면") {
            `when`("연결된 모집글이 있으면") {
                val host = UserFixtures.user()
                val party = party()
                val oldPlan = PlanFixtures.plan(owner = host)
                party.plan = oldPlan
                val newPlan = PlanFixtures.plan(owner = host)
                val linkedPost = post(party, host).apply { this.plan = oldPlan; this.isPlanPublic = true }
                val request = UpdatePartyRequest(
                    itinStart = party.itinStart,
                    itinFinish = party.itinFinish,
                    destination = party.destination,
                    capacity = party.capacity,
                    name = "New name",
                    planUuid = newPlan.uuid,
                )
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { partyRepository.findByUuidForUpdate(party.uuid) } returns party
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership(party, host)
                every { planRepository.findByUuid(newPlan.uuid) } returns newPlan
                every { postRepository.findByParty_Uuid(party.uuid) } returns linkedPost

                then("연결된 모집글의 plan도 새 계획서로 교체된다") {
                    sut.updateParty(party.uuid, request)
                    linkedPost.plan shouldBe newPlan
                    linkedPost.isPlanPublic shouldBe true
                }
            }
        }

        given("파티 이미지 상태 전환") {
            `when`("호스트가 이미지 업로드를 확인하면") {
                val host = UserFixtures.user()
                val party = party()
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership(party, host)

                then("imageState가 CUSTOM으로 바뀐다") {
                    sut.confirmPartyImage(party.uuid)
                    party.imageState shouldBe PartyImageState.CUSTOM
                }
            }

            `when`("파티 이미지가 삭제되면") {
                val party = party().apply { imageState = PartyImageState.CUSTOM }
                every { partyRepository.findByUuid(party.uuid) } returns party

                then("imageState가 NONE으로 바뀌어 기본 이미지를 표시한다") {
                    sut.clearPartyImageState(party.uuid)
                    party.imageState shouldBe PartyImageState.NONE
                }
            }
        }

        given("파티 이미지 다운로드 PAR 조회 시") {
            `when`("imageState가 NONE이면") {
                val party = party().apply { imageState = PartyImageState.NONE }
                every { partyRepository.findByUuid(party.uuid) } returns party

                then("null을 반환해 기본 이미지를 쓰도록 한다") {
                    sut.resolvePartyOriginalImageDownload(party.uuid) shouldBe null
                }
            }

            `when`("imageState가 INHERITED이고 연결된 모집글에 이미지가 없으면") {
                val party = party().apply { imageState = PartyImageState.INHERITED }
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { postRepository.findByParty_Uuid(party.uuid) } returns null

                then("null을 반환한다") {
                    sut.resolvePartyOriginalImageDownload(party.uuid) shouldBe null
                }
            }
        }

        given("파티 종료 시") {
            `when`("연결된 plan이 있으면") {
                val host = UserFixtures.user()
                val party = party()
                val plan = PlanFixtures.plan(owner = host)
                val marker = PlanFixtures.marker(plan)
                party.plan = plan
                every { partyRepository.findByUuid(party.uuid) } returns party
                every { currentUserProvider.uuid } returns host.uuid
                every { userPartyRepository.findByParty_UuidAndUser_Uuid(party.uuid, host.uuid) } returns hostMembership(party, host)
                every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns listOf(marker)

                then("planSnapshot이 JSON으로 저장된다") {
                    sut.completeParty(party.uuid)
                    party.planSnapshot shouldNotBe null
                    party.planSnapshot!!.contains(plan.destination) shouldBe true
                }
            }
        }
    }
}
