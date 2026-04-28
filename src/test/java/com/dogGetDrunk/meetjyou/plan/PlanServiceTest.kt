package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.notFound.PlanNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.notFound.UserNotFoundException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanReadAccessDeniedException
import com.dogGetDrunk.meetjyou.common.exception.business.plan.PlanUpdateAccessDeniedException
import com.dogGetDrunk.meetjyou.common.util.SecurityUtil
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.plan.dto.UpdatePlanRequest
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
import com.dogGetDrunk.meetjyou.user.UserRepository
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

class PlanServiceTest : BehaviorSpec() {
    private val planRepository: PlanRepository = mockk(relaxed = true)
    private val markerRepository: MarkerRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val postRepository: PostRepository = mockk(relaxed = true)
    private val userPartyRepository: UserPartyRepository = mockk(relaxed = true)
    private val sut = PlanService(planRepository, markerRepository, userRepository, postRepository, userPartyRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach {
            clearAllMocks()
            mockkObject(SecurityUtil)
        }
        afterSpec { unmockkAll() }

        // ── createPlan ───────────────────────────────────────────────────────

        given("createPlan 호출 시") {
            val owner = UserFixtures.user()
            val request = CreatePlanRequest(
                itinStart = Instant.parse("2026-05-01T00:00:00Z"),
                itinFinish = Instant.parse("2026-05-05T00:00:00Z"),
                location = "Seoul",
                centerLat = 37.5665,
                centerLng = 126.9780,
                memo = null,
                markers = listOf(
                    CreateMarkerRequest(
                        lat = 37.5665, lng = 126.9780,
                        date = Instant.parse("2026-05-01T10:00:00Z"),
                        dayNum = 1, idx = 0,
                        place = "Gyeongbokgung", memo = null,
                    )
                ),
            )

            `when`("유저가 존재하면") {
                then("Plan과 Marker를 저장하고 응답을 반환한다") {
                    every { SecurityUtil.getCurrentUserUuid() } returns owner.uuid
                    every { userRepository.findByUuid(owner.uuid) } returns owner
                    every { planRepository.save(any()) } returnsArgument 0

                    val result = sut.createPlan(request)

                    verify(exactly = 1) { planRepository.save(any()) }
                    verify(exactly = 1) { markerRepository.saveAll(any<List<Marker>>()) }
                    result.destination shouldBe "Seoul"
                    result.userUuid shouldBe owner.uuid
                }
            }

            `when`("유저가 존재하지 않으면") {
                then("UserNotFoundException을 던진다") {
                    every { SecurityUtil.getCurrentUserUuid() } returns owner.uuid
                    every { userRepository.findByUuid(owner.uuid) } returns null

                    shouldThrow<UserNotFoundException> {
                        sut.createPlan(request)
                    }
                }
            }
        }

        // ── getMyPlans ───────────────────────────────────────────────────────

        given("getMyPlans 호출 시") {
            `when`("현재 유저가 인증되어 있으면") {
                then("소유한 계획서 목록을 페이지네이션으로 반환한다") {
                    val owner = UserFixtures.user()
                    val plan = PlanFixtures.plan(owner)
                    val page = PageImpl(listOf(plan))

                    every { SecurityUtil.getCurrentUserUuid() } returns owner.uuid
                    every { planRepository.findAllByOwner_Uuid(owner.uuid, any()) } returns page
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns emptyList()

                    val result = sut.getMyPlans(Pageable.unpaged())

                    result.totalElements shouldBe 1
                    result.content[0].userUuid shouldBe owner.uuid
                }
            }
        }

        // ── getPlanByUuid ────────────────────────────────────────────────────

        given("getPlanByUuid 호출 시") {
            val owner = UserFixtures.user()
            val plan = PlanFixtures.plan(owner)

            `when`("소유자가 조회하면") {
                then("Plan을 반환한다") {
                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns owner.uuid
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns emptyList()

                    val result = sut.getPlanByUuid(plan.uuid)

                    result.uuid shouldBe plan.uuid
                }
            }

            `when`("타 유저가 조회하고 모집글에 isPlanPublic=true로 첨부되어 있으면") {
                then("Plan을 반환한다") {
                    val otherUserUuid = UUID.randomUUID()

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns otherUserUuid
                    every { postRepository.existsByPlan_UuidAndIsPlanPublicTrue(plan.uuid) } returns true
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns emptyList()

                    val result = sut.getPlanByUuid(plan.uuid)

                    result.uuid shouldBe plan.uuid
                }
            }

            `when`("타 유저가 조회하고 본인이 속한 파티에 첨부되어 있으면") {
                then("Plan을 반환한다") {
                    val otherUserUuid = UUID.randomUUID()

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns otherUserUuid
                    every { postRepository.existsByPlan_UuidAndIsPlanPublicTrue(plan.uuid) } returns false
                    every {
                        userPartyRepository.existsByParty_Plan_UuidAndUser_UuidAndMemberStatus(
                            plan.uuid, otherUserUuid, MemberStatus.JOINED,
                        )
                    } returns true
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(plan.uuid) } returns emptyList()

                    val result = sut.getPlanByUuid(plan.uuid)

                    result.uuid shouldBe plan.uuid
                }
            }

            `when`("타 유저가 조회하고 세 조건 모두 해당하지 않으면") {
                then("PlanReadAccessDeniedException을 던진다") {
                    val otherUserUuid = UUID.randomUUID()

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns otherUserUuid
                    every { postRepository.existsByPlan_UuidAndIsPlanPublicTrue(plan.uuid) } returns false
                    every {
                        userPartyRepository.existsByParty_Plan_UuidAndUser_UuidAndMemberStatus(
                            plan.uuid, otherUserUuid, MemberStatus.JOINED,
                        )
                    } returns false

                    shouldThrow<PlanReadAccessDeniedException> {
                        sut.getPlanByUuid(plan.uuid)
                    }
                }
            }

            `when`("Plan이 존재하지 않으면") {
                then("PlanNotFoundException을 던진다") {
                    val unknownUuid = UUID.randomUUID()
                    every { planRepository.findByUuid(unknownUuid) } returns null

                    shouldThrow<PlanNotFoundException> {
                        sut.getPlanByUuid(unknownUuid)
                    }
                }
            }
        }

        // ── updatePlan ───────────────────────────────────────────────────────

        given("updatePlan 호출 시") {
            val owner = UserFixtures.user()
            val plan = PlanFixtures.plan(owner)
            val request = UpdatePlanRequest(
                itinStart = Instant.parse("2026-06-01T00:00:00Z"),
                itinFinish = Instant.parse("2026-06-05T00:00:00Z"),
                location = "Busan",
                centerLat = 35.1796,
                centerLng = 129.0756,
                memo = "updated",
                favorite = true,
            )

            `when`("소유자가 수정하면") {
                then("Plan 필드가 업데이트된 응답을 반환한다") {
                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns owner.uuid

                    val result = sut.updatePlan(plan.uuid, request)

                    result.destination shouldBe "Busan"
                    result.favorite shouldBe true
                }
            }

            `when`("소유자가 아닌 유저가 수정하면") {
                then("PlanUpdateAccessDeniedException을 던진다") {
                    val otherUserUuid = UUID.randomUUID()

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns otherUserUuid

                    shouldThrow<PlanUpdateAccessDeniedException> {
                        sut.updatePlan(plan.uuid, request)
                    }
                }
            }

            `when`("Plan이 존재하지 않으면") {
                then("PlanNotFoundException을 던진다") {
                    val unknownUuid = UUID.randomUUID()
                    every { planRepository.findByUuid(unknownUuid) } returns null

                    shouldThrow<PlanNotFoundException> {
                        sut.updatePlan(unknownUuid, request)
                    }
                }
            }
        }

        // ── deletePlan ───────────────────────────────────────────────────────

        given("deletePlan 호출 시") {
            val owner = UserFixtures.user()
            val plan = PlanFixtures.plan(owner)

            `when`("소유자가 삭제하면") {
                then("Marker와 Plan을 모두 삭제한다") {
                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns owner.uuid

                    sut.deletePlan(plan.uuid)

                    verify(exactly = 1) { markerRepository.deleteAllByPlan(plan) }
                    verify(exactly = 1) { planRepository.delete(plan) }
                }
            }

            `when`("소유자가 아닌 유저가 삭제하면") {
                then("PlanUpdateAccessDeniedException을 던진다") {
                    val otherUserUuid = UUID.randomUUID()

                    every { planRepository.findByUuid(plan.uuid) } returns plan
                    every { SecurityUtil.getCurrentUserUuid() } returns otherUserUuid

                    shouldThrow<PlanUpdateAccessDeniedException> {
                        sut.deletePlan(plan.uuid)
                    }
                }
            }
        }
    }
}
