package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanReadAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class MarkerServiceTest : BehaviorSpec() {
    private val markerRepository: MarkerRepository = mockk(relaxed = true)
    private val planRepository: PlanRepository = mockk(relaxed = true)
    private val postRepository: PostRepository = mockk(relaxed = true)
    private val userPartyRepository: UserPartyRepository = mockk(relaxed = true)
    private val planAccessGuard = PlanAccessGuard(postRepository, userPartyRepository)
    private val currentUserProvider: CurrentUserProvider = mockk(relaxed = true)
    private val sut = MarkerService(markerRepository, planRepository, planAccessGuard, currentUserProvider)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        // ── getMarkersByPlan ─────────────────────────────────────────────────

        given("getMarkersByPlan 호출 시") {
            `when`("소유자가 조회하면") {
                then("dayNum, idx 순으로 정렬된 마커 목록을 반환한다") {
                    val owner = UserFixtures.user()
                    val plan = PlanFixtures.plan(owner)
                    val markers = listOf(
                        PlanFixtures.marker(plan, dayNum = 1, idx = 1),
                        PlanFixtures.marker(plan, dayNum = 1, idx = 0),
                    )

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { currentUserProvider.uuid } returns owner.uuid
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns markers

                    val result = sut.getMarkersByPlan(plan.uuid)

                    result.size shouldBe 2
                }
            }

            `when`("plan이 존재하지 않으면") {
                then("PlanNotFoundException을 던진다") {
                    val unknownUuid = UUID.randomUUID()
                    every { planRepository.findByUuid(unknownUuid) } returns null

                    shouldThrow<PlanNotFoundException> {
                        sut.getMarkersByPlan(unknownUuid)
                    }
                }
            }

            `when`("소유자도 아니고 공개 모집글/참여 파티에도 해당하지 않으면") {
                then("PlanReadAccessDeniedException을 던진다") {
                    val owner = UserFixtures.user()
                    val plan = PlanFixtures.plan(owner)
                    val otherUserUuid = UUID.randomUUID()

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { currentUserProvider.uuid } returns otherUserUuid
                    every { postRepository.existsByPlan_UuidAndIsPlanPublicTrue(plan.uuid) } returns false
                    every {
                        userPartyRepository.existsByParty_Plan_UuidAndUser_UuidAndMemberStatus(
                            plan.uuid, otherUserUuid, MemberStatus.JOINED,
                        )
                    } returns false

                    shouldThrow<PlanReadAccessDeniedException> {
                        sut.getMarkersByPlan(plan.uuid)
                    }
                }
            }
        }

        // ── replaceMarkers ───────────────────────────────────────────────────

        given("replaceMarkers 호출 시") {
            val owner = UserFixtures.user()
            val plan = PlanFixtures.plan(owner)
            val markerRequests = listOf(
                CreateMarkerRequest(
                    lat = 37.5665, lng = 126.9780,
                    date = Instant.parse("2026-05-01T10:00:00Z"),
                    dayNum = 1, idx = 0,
                    place = "Gyeongbokgung", memo = null,
                )
            )

            `when`("plan이 존재하고 본인 소유이면") {
                then("기존 마커를 삭제하고 새 마커를 저장한 뒤, 재조회 없이 저장한 목록을 그대로 반환한다") {
                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { currentUserProvider.uuid } returns owner.uuid

                    val result = sut.replaceMarkers(plan.uuid, markerRequests)

                    verify(exactly = 1) { markerRepository.deleteAllByPlan(plan) }
                    verify(exactly = 1) { markerRepository.saveAll(any<List<Marker>>()) }
                    verify(exactly = 0) { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(any()) }
                    result.size shouldBe 1
                }
            }

            `when`("plan이 존재하지 않으면") {
                then("PlanNotFoundException을 던진다") {
                    val unknownUuid = UUID.randomUUID()
                    every { planRepository.findByUuid(unknownUuid) } returns null

                    shouldThrow<PlanNotFoundException> {
                        sut.replaceMarkers(unknownUuid, markerRequests)
                    }
                }
            }

            `when`("현재 사용자가 plan 소유자가 아니면") {
                then("PlanUpdateAccessDeniedException을 던진다") {
                    val otherUserUuid = UUID.randomUUID()

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { currentUserProvider.uuid } returns otherUserUuid

                    shouldThrow<PlanUpdateAccessDeniedException> {
                        sut.replaceMarkers(plan.uuid, markerRequests)
                    }
                }
            }
        }
    }
}
