package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.place.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
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
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.UUID

// Regression tests for the party-module N+1 fixes: getAllParties / getPartiesByPlanUuid /
// getPartiesByUserUuid must go through the join-fetch repository methods, not the plain
// findAll ones that force a lazy load of party.plan per row.
class PartyQueryOptimizationTest : BehaviorSpec() {

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
        chatParticipantService, chatRoomEventBroadcaster, userPartyRepository, userRepository,
        publisher, objectMapper, currentUserProvider, PlaceTestDoubles.regionCatalog(),
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        val user = UserFixtures.user()
        val party = NotificationCenterFixtures.party()
        val pageable = PageRequest.of(0, 10)

        given("전체 파티 목록 조회 시") {
            `when`("getAllParties를 호출하면") {
                then("plan을 LEFT JOIN FETCH하는 findAllWithPlan을 사용하고 findAll은 호출하지 않는다") {
                    every { partyRepository.findAllWithPlan(pageable) } returns PageImpl(listOf(party))

                    sut.getAllParties(pageable)

                    verify(exactly = 1) { partyRepository.findAllWithPlan(pageable) }
                    verify(exactly = 0) { partyRepository.findAll(pageable) }
                }
            }
        }

        given("plan 기준 파티 목록 조회 시") {
            `when`("getPartiesByPlanUuid를 호출하면") {
                then("plan을 LEFT JOIN FETCH하는 findAllByPlanUuidWithPlan을 사용한다") {
                    val planUuid = UUID.randomUUID()
                    every { partyRepository.findAllByPlanUuidWithPlan(planUuid, pageable) } returns PageImpl(listOf(party))

                    sut.getPartiesByPlanUuid(planUuid, pageable)

                    verify(exactly = 1) { partyRepository.findAllByPlanUuidWithPlan(planUuid, pageable) }
                }
            }
        }

        given("유저 기준 파티 목록 조회 시") {
            `when`("getPartiesByUserUuid를 호출하면") {
                then("party를 JOIN FETCH하는 findAllWithPartyByUserUuid를 사용한다") {
                    val userParty = UserParty(party, user, PartyRole.MEMBER)
                    every { userPartyRepository.findAllWithPartyByUserUuid(user.uuid, pageable) } returns PageImpl(listOf(userParty))

                    val result = sut.getPartiesByUserUuid(user.uuid, pageable)

                    result.content.single().uuid shouldBe party.uuid
                    verify(exactly = 1) { userPartyRepository.findAllWithPartyByUserUuid(user.uuid, pageable) }
                }
            }
        }
    }
}
