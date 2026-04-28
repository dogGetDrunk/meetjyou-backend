package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
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
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class MarkerServiceTest : BehaviorSpec() {
    private val markerRepository: MarkerRepository = mockk(relaxed = true)
    private val planRepository: PlanRepository = mockk(relaxed = true)
    private val sut = MarkerService(markerRepository, planRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach {
            clearAllMocks()
            mockkObject(SecurityUtil)
        }
        afterSpec { unmockkAll() }

        // ── getMarkersByPlan ─────────────────────────────────────────────────

        given("getMarkersByPlan 호출 시") {
            `when`("plan이 존재하면") {
                then("dayNum, idx 순으로 정렬된 마커 목록을 반환한다") {
                    val owner = UserFixtures.user()
                    val plan = PlanFixtures.plan(owner)
                    val markers = listOf(
                        PlanFixtures.marker(plan, dayNum = 1, idx = 1),
                        PlanFixtures.marker(plan, dayNum = 1, idx = 0),
                    )

                    every { planRepository.existsByUuid(plan.uuid) } returns true
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns markers

                    val result = sut.getMarkersByPlan(plan.uuid)

                    result.size shouldBe 2
                }
            }

            `when`("plan이 존재하지 않으면") {
                then("PlanNotFoundException을 던진다") {
                    val unknownUuid = UUID.randomUUID()
                    every { planRepository.existsByUuid(unknownUuid) } returns false

                    shouldThrow<PlanNotFoundException> {
                        sut.getMarkersByPlan(unknownUuid)
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
                then("기존 마커를 삭제하고 새 마커를 저장한 뒤 목록을 반환한다") {
                    val savedMarker = PlanFixtures.marker(plan, dayNum = 1, idx = 0)

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns owner.uuid
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns listOf(savedMarker)

                    val result = sut.replaceMarkers(plan.uuid, markerRequests)

                    verify(exactly = 1) { markerRepository.deleteAllByPlan(plan) }
                    verify(exactly = 1) { markerRepository.saveAll(any<List<Marker>>()) }
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
                    every { SecurityUtil.getCurrentUserUuid() } returns otherUserUuid

                    shouldThrow<PlanUpdateAccessDeniedException> {
                        sut.replaceMarkers(plan.uuid, markerRequests)
                    }
                }
            }
        }
    }
}
