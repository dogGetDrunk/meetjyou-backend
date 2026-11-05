package com.dogGetDrunk.meetjyou.common.idempotency

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.idempotency.IdempotencyKeyConflictException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanAccessGuard
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.PlanService
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.plan.support.PlanFixtures
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.post.view.PostViewService
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
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
import io.mockk.verify
import java.time.Instant
import java.util.UUID

/**
 * AT-003-2: PostService / PlanService.resolveAfterConflict keep their recovery branches.
 * null from resolveExisting -> null (no resource lookup); UUID -> existing resource response;
 * a hash mismatch -> IDEMPOTENCY_KEY_REUSED. The real hash judgement stays in IdempotencyKeyService.
 */
class ConflictRecoveryServiceTest : BehaviorSpec() {

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val compPreferenceRepository = mockk<CompPreferenceRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val partyService = mockk<PartyService>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val postViewService = mockk<PostViewService>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val idempotencyKeyService = mockk<IdempotencyKeyService>(relaxed = true)
    private val idempotencyKeyRepository = mockk<IdempotencyKeyRepository>(relaxed = true)

    private val postSut = PostService(
        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
        partyService, planRepository, markerRepository, userPartyRepository, postViewService,
        currentUserProvider, chatRoomRepository, idempotencyKeyService,
    )
    private val planSut = PlanService(
        planRepository, markerRepository, userRepository, postRepository, partyRepository,
        PlanAccessGuard(postRepository, userPartyRepository), currentUserProvider, idempotencyKeyService,
    )

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun postRequest() = CreatePostRequest(
        title = "Trip",
        content = "content",
        isInstant = false,
        itinStart = Instant.now().plusSeconds(3600),
        itinFinish = Instant.now().plusSeconds(7200),
        location = "Seoul",
        capacity = 4,
        companionSpec = null,
        planUuid = null,
        isPlanPublic = null,
    )

    private fun planRequest() = CreatePlanRequest(
        title = "Seoul Trip",
        itinStart = Instant.parse("2026-05-01T00:00:00Z"),
        itinFinish = Instant.parse("2026-05-05T00:00:00Z"),
        location = "Seoul",
        centerLat = 37.5665,
        centerLng = 126.9780,
        memo = null,
        markers = emptyList(),
    )

    // A real key service over a mocked repository: the stored hash differs from the request's hash.
    private fun realKeyServiceWithStoredHash(
        scope: IdempotencyScope,
        user: com.dogGetDrunk.meetjyou.user.User,
        resourceUuid: UUID,
    ): IdempotencyKeyService {
        val stored = IdempotencyKey(
            scope = scope,
            user = user,
            idempotencyKey = KEY,
            resourceUuid = resourceUuid,
            requestHash = STORED_HASH,
        )
        every {
            idempotencyKeyRepository.findByScopeAndUser_IdAndIdempotencyKey(any(), any(), any())
        } returns stored
        return IdempotencyKeyService(idempotencyKeyRepository, ObjectMapper().findAndRegisterModules())
    }

    init {
        beforeEach { clearAllMocks() }

        val user = UserFixtures.user()
        val party = NotificationCenterFixtures.party()
        val chatRoom = ChatRoom(party = party)

        beforeEach {
            every { currentUserProvider.uuid } returns user.uuid
            every { currentUserProvider.user } returns user
            every { userRepository.findByUuid(user.uuid) } returns user
            every { idempotencyKeyService.hashRequest(any()) } returns REQUEST_HASH
        }

        registerPostNull(user)
        registerPostExisting(user, party, chatRoom)
        registerPostMismatch(user)
        registerPlanNull(user)
        registerPlanExisting(user)
        registerPlanMismatch(user)
        registerRealHashContractPost(user)
        registerRealHashContractPlan(user)
    }

    private fun registerPostNull(user: com.dogGetDrunk.meetjyou.user.User) {
        given("AT-003-2 PostService.resolveAfterConflict에서 resolveExisting이 null이면") {
            `when`("호출하면") {
                then("null을 반환하고 게시글 조회는 하지 않는다") {
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_POST, user, KEY, REQUEST_HASH)
                    } returns null

                    postSut.resolveAfterConflict(postRequest(), KEY) shouldBe null

                    verify(exactly = 0) { postRepository.findByUuid(any()) }
                    verify(exactly = 0) { compPreferenceRepository.findAllByPost(any()) }
                    verify(exactly = 0) { chatRoomRepository.findByParty_Uuid(any()) }
                }
            }
        }

    }

    private fun registerPostExisting(
        user: com.dogGetDrunk.meetjyou.user.User,
        party: com.dogGetDrunk.meetjyou.party.Party,
        chatRoom: ChatRoom,
    ) {
        given("AT-003-2 PostService.resolveAfterConflict에서 resolveExisting이 기존 UUID를 반환하면") {
            `when`("호출하면") {
                then("기존 게시글로 응답을 재구성한다") {
                    val existing: Post = NotificationCenterFixtures.post(party, user)
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_POST, user, KEY, REQUEST_HASH)
                    } returns existing.uuid
                    every { postRepository.findByUuid(existing.uuid) } returns existing
                    every { compPreferenceRepository.findAllByPost(existing) } returns emptyList()
                    every { chatRoomRepository.findByParty_Uuid(party.uuid) } returns chatRoom

                    val result = postSut.resolveAfterConflict(postRequest(), KEY)

                    result?.uuid shouldBe existing.uuid
                }
            }
        }

    }

    private fun registerPostMismatch(user: com.dogGetDrunk.meetjyou.user.User) {
        given("AT-003-2 PostService.resolveAfterConflict에서 resolveExisting이 해시 불일치를 던지면") {
            `when`("호출하면") {
                then("IDEMPOTENCY_KEY_REUSED 예외가 그대로 전파된다") {
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_POST, user, KEY, REQUEST_HASH)
                    } throws IdempotencyKeyConflictException(KEY)

                    val ex = shouldThrow<IdempotencyKeyConflictException> {
                        postSut.resolveAfterConflict(postRequest(), KEY)
                    }

                    ex.errorCode shouldBe ErrorCode.IDEMPOTENCY_KEY_REUSED
                }
            }
        }
    }

    private fun registerPlanNull(user: com.dogGetDrunk.meetjyou.user.User) {
        given("AT-003-2 PlanService.resolveAfterConflict에서 resolveExisting이 null이면") {
            `when`("호출하면") {
                then("null을 반환하고 플랜·마커 조회는 하지 않는다") {
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_PLAN, user, KEY, REQUEST_HASH)
                    } returns null

                    planSut.resolveAfterConflict(planRequest(), KEY) shouldBe null

                    verify(exactly = 0) { planRepository.findByUuid(any()) }
                    verify(exactly = 0) { markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(any()) }
                }
            }
        }

    }

    private fun registerPlanExisting(user: com.dogGetDrunk.meetjyou.user.User) {
        given("AT-003-2 PlanService.resolveAfterConflict에서 resolveExisting이 기존 UUID를 반환하면") {
            `when`("호출하면") {
                then("기존 플랜으로 응답을 재구성한다") {
                    val existing = PlanFixtures.plan(user)
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_PLAN, user, KEY, REQUEST_HASH)
                    } returns existing.uuid
                    every { planRepository.findByUuid(existing.uuid) } returns existing
                    every {
                        markerRepository.findAllByPlan_UuidOrderByDayNumAscIdxAsc(existing.uuid)
                    } returns emptyList()

                    val result = planSut.resolveAfterConflict(planRequest(), KEY)

                    result?.uuid shouldBe existing.uuid
                }
            }
        }

    }

    private fun registerPlanMismatch(user: com.dogGetDrunk.meetjyou.user.User) {
        given("AT-003-2 PlanService.resolveAfterConflict에서 resolveExisting이 해시 불일치를 던지면") {
            `when`("호출하면") {
                then("IDEMPOTENCY_KEY_REUSED 예외가 그대로 전파된다") {
                    every {
                        idempotencyKeyService.resolveExisting(IdempotencyScope.CREATE_PLAN, user, KEY, REQUEST_HASH)
                    } throws IdempotencyKeyConflictException(KEY)

                    val ex = shouldThrow<IdempotencyKeyConflictException> {
                        planSut.resolveAfterConflict(planRequest(), KEY)
                    }

                    ex.errorCode shouldBe ErrorCode.IDEMPOTENCY_KEY_REUSED
                }
            }
        }
    }

    private fun registerRealHashContractPost(user: com.dogGetDrunk.meetjyou.user.User) {
        given("AT-003-2 실제 IdempotencyKeyService에 저장된 해시가 요청 해시와 다르면 (게시글)") {
            `when`("PostService.resolveAfterConflict를 호출하면") {
                then("키 서비스 계약대로 IDEMPOTENCY_KEY_REUSED가 발생한다") {
                    val realKeys = realKeyServiceWithStoredHash(IdempotencyScope.CREATE_POST, user, UUID.randomUUID())
                    val sut = PostService(
                        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
                        partyService, planRepository, markerRepository, userPartyRepository, postViewService,
                        currentUserProvider, chatRoomRepository, realKeys,
                    )

                    val ex = shouldThrow<IdempotencyKeyConflictException> {
                        sut.resolveAfterConflict(postRequest(), KEY)
                    }

                    ex.errorCode shouldBe ErrorCode.IDEMPOTENCY_KEY_REUSED
                }
            }
        }
    }

    private fun registerRealHashContractPlan(user: com.dogGetDrunk.meetjyou.user.User) {
        given("AT-003-2 실제 IdempotencyKeyService에 저장된 해시가 요청 해시와 다르면 (플랜)") {
            `when`("PlanService.resolveAfterConflict를 호출하면") {
                then("키 서비스 계약대로 IDEMPOTENCY_KEY_REUSED가 발생한다") {
                    val realKeys = realKeyServiceWithStoredHash(IdempotencyScope.CREATE_PLAN, user, UUID.randomUUID())
                    val sut = PlanService(
                        planRepository, markerRepository, userRepository, postRepository, partyRepository,
                        PlanAccessGuard(postRepository, userPartyRepository), currentUserProvider, realKeys,
                    )

                    val ex = shouldThrow<IdempotencyKeyConflictException> {
                        sut.resolveAfterConflict(planRequest(), KEY)
                    }

                    ex.errorCode shouldBe ErrorCode.IDEMPOTENCY_KEY_REUSED
                }
            }
        }
    }

    private companion object {
        const val KEY = "key-at-003-2"
        const val REQUEST_HASH = "request-hash"
        const val STORED_HASH = "stored-hash-differs"
    }
}
