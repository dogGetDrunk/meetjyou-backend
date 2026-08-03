package com.dogGetDrunk.meetjyou.plan

import com.dogGetDrunk.meetjyou.common.exception.business.idempotency.IdempotencyKeyConflictException
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyService
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyScope
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class CreatePlanIdempotencyTest : BehaviorSpec() {

    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val planAccessGuard = PlanAccessGuard(postRepository, userPartyRepository)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val idempotencyKeyService = mockk<IdempotencyKeyService>(relaxed = true)
    private val sut = PlanService(
        planRepository, markerRepository, userRepository, postRepository, partyRepository,
        planAccessGuard, currentUserProvider, idempotencyKeyService,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun request() = CreatePlanRequest(
        title = "Seoul Trip",
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

    init {
        beforeEach { clearAllMocks() }

        val owner = UserFixtures.user()

        beforeEach {
            every { currentUserProvider.user } returns owner
        }

        given("createPlan 호출 시 idempotencyKey가 없으면") {
            `when`("호출하면") {
                then("기존과 동일하게 생성하고 idempotencyKeyService와는 상호작용하지 않는다") {
                    every { planRepository.save(any()) } returnsArgument 0

                    sut.createPlan(request(), null)

                    verify(exactly = 1) { planRepository.save(any()) }
                    verify(exactly = 0) { idempotencyKeyService.resolveExisting(any(), any(), any(), any()) }
                    verify(exactly = 0) { idempotencyKeyService.record(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("createPlan 호출 시 idempotencyKey가 있고 기존 매치가 없으면") {
            `when`("호출하면") {
                then("정상 생성 후 record()가 1회 호출된다") {
                    every { idempotencyKeyService.hashRequest(any()) } returns "hash-1"
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_PLAN, owner, "key-1", "hash-1")
                    } returns null
                    every { planRepository.save(any()) } returnsArgument 0

                    sut.createPlan(request(), "key-1")

                    verify(exactly = 1) { planRepository.save(any()) }
                    verify(exactly = 1) {
                        idempotencyKeyService.record(IdempotencyScope.CREATE_PLAN, owner, "key-1", any(), "hash-1")
                    }
                }
            }
        }

        given("createPlan 호출 시 idempotencyKey가 있고 기존 매치(같은 바디)가 있으면") {
            `when`("호출하면") {
                then("생성 로직을 건너뛰고 기존 리소스로 응답을 재구성한다") {
                    val existingPlan = PlanFixtures.plan(owner)

                    every { idempotencyKeyService.hashRequest(any()) } returns "hash-2"
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_PLAN, owner, "key-2", "hash-2")
                    } returns existingPlan.uuid
                    every { planRepository.findByUuid(existingPlan.uuid) } returns existingPlan
                    every { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(existingPlan.uuid) } returns emptyList()

                    val result = sut.createPlan(request(), "key-2")

                    result.uuid shouldBe existingPlan.uuid
                    verify(exactly = 0) { planRepository.save(any()) }
                    verify(exactly = 0) { markerRepository.saveAll(any<List<Marker>>()) }
                    verify(exactly = 0) { idempotencyKeyService.record(any(), any(), any(), any(), any()) }
                }
            }
        }

        given("createPlan 호출 시 idempotencyKey가 있고 기존 매치(다른 바디)가 있으면") {
            `when`("호출하면") {
                then("IdempotencyKeyConflictException을 던지고 생성 로직은 실행되지 않는다") {
                    every { idempotencyKeyService.hashRequest(any()) } returns "hash-3"
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_PLAN, owner, "key-3", "hash-3")
                    } throws IdempotencyKeyConflictException("key-3")

                    shouldThrow<IdempotencyKeyConflictException> {
                        sut.createPlan(request(), "key-3")
                    }

                    verify(exactly = 0) { planRepository.save(any()) }
                }
            }
        }
    }
}
