package com.dogGetDrunk.meetjyou.common.idempotency

import com.dogGetDrunk.meetjyou.place.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanAccessGuard
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.PlanService
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

/**
 * AT-002-1: the real create services + the real IdempotencyKeyService reject a 256-char
 * Idempotency-Key before any lookup or write; 255 chars and null keep the old behavior.
 * Only public methods are used, so the helper name in IdempotencyKeyService is not assumed.
 */
class IdempotencyKeyLengthServiceTest : BehaviorSpec() {

    private val keyRepository = mockk<IdempotencyKeyRepository>(relaxed = true)
    private val keyService = IdempotencyKeyService(keyRepository, ObjectMapper().registerModule(JavaTimeModule()))

    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val compPreferenceRepository = mockk<CompPreferenceRepository>(relaxed = true)
    private val preferenceRepository = mockk<PreferenceRepository>(relaxed = true)
    private val partyService = mockk<PartyService>(relaxed = true)
    private val planRepository = mockk<PlanRepository>(relaxed = true)
    private val markerRepository = mockk<MarkerRepository>(relaxed = true)
    private val userPartyRepository = mockk<UserPartyRepository>(relaxed = true)
    private val postViewService = mockk<PostViewService>(relaxed = true)
    private val chatRoomRepository = mockk<ChatRoomRepository>(relaxed = true)
    private val partyRepository = mockk<PartyRepository>(relaxed = true)
    private val currentUserProvider = mockk<CurrentUserProvider>(relaxed = true)

    private val postService = PostService(
        postRepository, userRepository, compPreferenceRepository, preferenceRepository,
        partyService, planRepository, markerRepository, userPartyRepository, postViewService,
        currentUserProvider, chatRoomRepository, keyService,
            PlaceTestDoubles.resolver(), PlaceTestDoubles.regionCatalog(),
    )
    private val planService = PlanService(
        planRepository, markerRepository, userRepository, postRepository, partyRepository,
        PlanAccessGuard(postRepository, userPartyRepository), currentUserProvider, keyService,
            PlaceTestDoubles.resolver(), PlaceTestDoubles.regionCatalog(),
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
    )

    private fun stubUser() {
        val user = UserFixtures.user()
        every { currentUserProvider.user } returns user
        every { currentUserProvider.uuid } returns user.uuid
        every { userRepository.findByUuid(user.uuid) } returns user
    }

    private fun stubNoExistingKeyRow() {
        every { keyRepository.findByScopeAndUser_IdAndIdempotencyKey(any(), any(), any()) } returns null
    }

    private fun verifyNoRepositoryTouched() {
        verify(exactly = 0) { keyRepository.findByScopeAndUser_IdAndIdempotencyKey(any(), any(), any()) }
        verify(exactly = 0) { keyRepository.save(any()) }
        verify(exactly = 0) { postRepository.save(any()) }
        verify(exactly = 0) { planRepository.save(any()) }
        verify(exactly = 0) { markerRepository.saveAll(any<List<com.dogGetDrunk.meetjyou.plan.Marker>>()) }
        verify(exactly = 0) { partyService.createParty(any()) }
    }

    init {
        beforeEach {
            clearAllMocks()
            stubUser()
        }

        given("AT-002-1 256-char Idempotency-Key on createPost") {
            `when`("createPost is called") {
                then("it throws INVALID_INPUT_VALUE and touches no repository") {
                    val ex = shouldThrow<InvalidInputException> {
                        postService.createPost(postRequest(), "k".repeat(MAX_KEY_LENGTH + 1))
                    }

                    ex.errorCode shouldBe ErrorCode.INVALID_INPUT_VALUE
                    verifyNoRepositoryTouched()
                }
            }
        }

        given("AT-002-1 256-char Idempotency-Key on createPlan") {
            `when`("createPlan is called") {
                then("it throws INVALID_INPUT_VALUE and touches no repository") {
                    val ex = shouldThrow<InvalidInputException> {
                        planService.createPlan(planRequest(), "k".repeat(MAX_KEY_LENGTH + 1))
                    }

                    ex.errorCode shouldBe ErrorCode.INVALID_INPUT_VALUE
                    verifyNoRepositoryTouched()
                }
            }
        }

        given("AT-002-1 255-char Idempotency-Key") {
            `when`("createPost is called") {
                then("the length check passes and the key lookup runs") {
                    stubNoExistingKeyRow()

                    val failure = runCatching { postService.createPost(postRequest(), "k".repeat(MAX_KEY_LENGTH)) }
                        .exceptionOrNull()

                    failure.shouldNotBeInstanceOf<InvalidInputException>()
                    verify(exactly = 1) { keyRepository.findByScopeAndUser_IdAndIdempotencyKey(any(), any(), any()) }
                }
            }

            `when`("createPlan is called") {
                then("the length check passes and the key lookup runs") {
                    stubNoExistingKeyRow()

                    val failure = runCatching { planService.createPlan(planRequest(), "k".repeat(MAX_KEY_LENGTH)) }
                        .exceptionOrNull()

                    failure.shouldNotBeInstanceOf<InvalidInputException>()
                    verify(exactly = 1) { keyRepository.findByScopeAndUser_IdAndIdempotencyKey(any(), any(), any()) }
                }
            }
        }

        given("AT-002-1 null Idempotency-Key") {
            `when`("createPost and createPlan are called") {
                then("idempotency handling is skipped and no key row is read or written") {
                    runCatching { postService.createPost(postRequest(), null) }
                    runCatching { planService.createPlan(planRequest(), null) }

                    verify(exactly = 0) { keyRepository.findByScopeAndUser_IdAndIdempotencyKey(any(), any(), any()) }
                    verify(exactly = 0) { keyRepository.save(any()) }
                }
            }
        }
    }

    private companion object {
        const val MAX_KEY_LENGTH = 255
    }
}
