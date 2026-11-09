package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.place.support.PlaceTestDoubles
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyCapacityBelowJoinedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.Plan
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyService
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.party.PartyRecruitmentStatus
import com.dogGetDrunk.meetjyou.party.PartyProgressStatus
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.PostService
import com.dogGetDrunk.meetjyou.post.PostStatus
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.UpdatePostStatusRequest
import com.dogGetDrunk.meetjyou.preference.CompPreferenceRepository
import com.dogGetDrunk.meetjyou.preference.PreferenceRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import com.dogGetDrunk.meetjyou.post.dto.CompanionSpec
import com.dogGetDrunk.meetjyou.preference.CompPreference
import com.dogGetDrunk.meetjyou.preference.Preference
import com.dogGetDrunk.meetjyou.preference.PreferenceType
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyRecruitmentClosedException
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles

// Services use real repositories inside TransactionTemplate to reproduce commit boundaries.
@SpringBootTest
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class, AuthenticationDetailsProvider::class, ObjectStorageClient::class, WorkRequestClient::class,
)
class PostPartySyncIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var parties: PartyRepository
    @Autowired private lateinit var posts: PostRepository
    @Autowired private lateinit var plans: PlanRepository
    @Autowired private lateinit var markers: MarkerRepository
    @Autowired private lateinit var rooms: ChatRoomRepository
    @Autowired private lateinit var memberships: UserPartyRepository
    @Autowired private lateinit var compPreferences: CompPreferenceRepository
    @Autowired private lateinit var preferences: PreferenceRepository
    @Autowired private lateinit var manager: PlatformTransactionManager
    private val current = ThreadLocal<UUID>()
    private val provider = mockk<CurrentUserProvider>()
    private val tx by lazy { TransactionTemplate(manager) }
    private val partyService by lazy {
        PartyService(parties, posts, plans, markers, rooms, mockk(relaxed = true), mockk(relaxed = true),
            memberships, users, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), ObjectMapper(),
            provider, PlaceTestDoubles.regionCatalog())
    }
    private val service by lazy {
        PostService(posts, users, compPreferences, preferences, partyService, plans, markers, memberships,
            mockk(relaxed = true), provider, rooms, mockk(relaxed = true),
                PlaceTestDoubles.resolver(), PlaceTestDoubles.regionCatalog())
    }
    private data class Seed(val host: UUID, val applicant: UUID, val party: UUID, val post: UUID)
    private fun user(): User {
        val tag = UUID.randomUUID().toString().take(TAG_LENGTH)
        val user = User(
            email = "$tag@example.com", nickname = "h$tag", authProvider = AuthProvider.KAKAO, externalId = tag,
        )
        return users.save(user)
    }
    private fun seed(joined: Int = INITIAL_JOINED, capacity: Int = INITIAL_CAPACITY): Seed = tx.execute {
        val host = user()
        val applicant = user()
        val party = parties.save(Party(itinStart = START, itinFinish = FINISH, destination = LOCATION,
            joined = joined, capacity = capacity, name = "Party"))
        memberships.save(UserParty(party, host, PartyRole.HOST))
        val post = Post(party, false, "Old title", "content", START, FINISH, LOCATION, capacity)
        post.author = host
        posts.save(post)
        Seed(host.uuid, applicant.uuid, party.uuid, post.uuid)
    } ?: error("seed failed")
    private data class LinkedSeed(
        val seed: Seed, val oldPlan: UUID, val newPlan: UUID, val preference: Long, val newName: String,
    )

    private fun linkedSeed(joined: Int, capacity: Int): LinkedSeed {
        val seed = seed(joined, capacity)
        return tx.execute {
            val post = posts.findByUuid(seed.post) ?: error("post missing")
            val host = post.author
            val oldPlan = plans.save(Plan("Old plan", START, FINISH, LOCATION, 0.0, 0.0, owner = host))
            val newPlan = plans.save(Plan("New plan", NEW_START, NEW_FINISH, "Jeju", 0.0, 0.0, owner = host))
            post.plan = oldPlan
            post.isPlanPublic = true
            post.party.plan = oldPlan
            val oldPreference = preferences.save(Preference(PreferenceType.GENDER, UUID.randomUUID().toString()))
            val newName = UUID.randomUUID().toString()
            preferences.save(Preference(PreferenceType.GENDER, newName))
            compPreferences.save(CompPreference(post, oldPreference))
            LinkedSeed(seed, oldPlan.uuid, newPlan.uuid, oldPreference.id, newName)
        } ?: error("linked seed failed")
    }

    private fun changedRequest(linked: LinkedSeed, capacity: Int, plan: UUID = linked.newPlan): UpdatePostRequest {
        val companion = mockk<CompanionSpec>()
        every { companion.formalizeTypesToNames() } returns mapOf(PreferenceType.GENDER to listOf(linked.newName))
        return request(capacity).copy(
            content = "New content", isInstant = true, companionSpec = companion, planUuid = plan, isPlanPublic = false,
        )
    }

    private fun inspectPreserved(linked: LinkedSeed) = tx.executeWithoutResult {
        val post = posts.findByUuid(linked.seed.post) ?: error("post missing")
        val party = parties.findByUuid(linked.seed.party) ?: error("party missing")
        post.plan?.uuid shouldBe linked.oldPlan
        party.plan?.uuid shouldBe linked.oldPlan
        post.isPlanPublic shouldBe true
        compPreferences.findAllByPost(post).map { it.preference.id }.toSet() shouldBe setOf(linked.preference)
        post.content shouldBe "content"
        post.isInstant shouldBe false
        post.itinStart shouldBe START
        post.itinFinish shouldBe FINISH
        post.location shouldBe LOCATION
        party.itinStart shouldBe START
        party.itinFinish shouldBe FINISH
        party.destination shouldBe LOCATION
    }

    private class FailingCompPreferences(delegate: CompPreferenceRepository) : CompPreferenceRepository by delegate {
        override fun <S : CompPreference> saveAll(entities: Iterable<S>): MutableList<S> =
            error(FORCED_FAILURE)
    }
    private fun request(capacity: Int) = UpdatePostRequest("New title", "content", false, NEW_START, NEW_FINISH,
        "Jeju", capacity, null, null, null)
    private fun runAs(uuid: UUID, action: () -> Unit): Result<Unit> = runCatching {
        current.set(uuid)
        try { tx.executeWithoutResult { action() } } finally { current.remove() }
    }

    private fun inspect(seed: Seed, capacity: Int, joined: Int, title: String) = tx.executeWithoutResult {
        val post = posts.findByUuid(seed.post) ?: error("post missing")
        val party = parties.findByUuid(seed.party) ?: error("party missing")
        post.capacity shouldBe capacity
        party.capacity shouldBe capacity
        party.joined shouldBe joined
        post.title shouldBe title
    }
    private fun edit(seed: Seed, capacity: Int) = runAs(seed.host) { service.updatePost(seed.post, request(capacity)) }
    private fun status(seed: Seed, value: PostStatus) = runAs(seed.host) {
        service.updatePostStatus(seed.post, UpdatePostStatusRequest(value))
    }
    private fun requestJoin(seed: Seed, note: String = "Jeju trip") =
        runAs(seed.applicant) { partyService.requestJoinParty(seed.party, note) }
    private fun approve(seed: Seed) = runAs(seed.host) { partyService.approveJoinRequest(seed.party, seed.applicant) }
    private fun memberStatus(seed: Seed) =
        memberships.findByParty_UuidAndUser_Uuid(seed.party, seed.applicant)?.memberStatus

    init {
        extensions(SpringExtension())
        beforeSpec { every { provider.uuid } answers { current.get() } }
        given("AT-001-1 AT-002-1 게시글과 파티의 정원이 같을 때") {
            `when`("정원과 일정을 수정하거나 참여 인원과 같게 줄이면") {
                then("두 엔티티의 필드가 동기화되고 경계값을 허용한다") {
                    val seed = seed(JOINED_BEFORE_APPROVAL, INITIAL_CAPACITY)
                    edit(seed, INCREASED_CAPACITY).isSuccess shouldBe true
                    inspect(seed, INCREASED_CAPACITY, JOINED_BEFORE_APPROVAL, "New title")
                    tx.executeWithoutResult {
                        val post = posts.findByUuid(seed.post) ?: error("post missing")
                        val party = parties.findByUuid(seed.party) ?: error("party missing")
                        post.itinStart shouldBe NEW_START
                        party.itinStart shouldBe NEW_START
                        post.itinFinish shouldBe NEW_FINISH
                        party.itinFinish shouldBe NEW_FINISH
                        post.location shouldBe "Jeju"
                        party.destination shouldBe "Jeju"
                    }
                    edit(seed, JOINED_BEFORE_APPROVAL).isSuccess shouldBe true
                    inspect(seed, JOINED_BEFORE_APPROVAL, JOINED_BEFORE_APPROVAL, "New title")
                }
            }
        }
        given("AT-001-2 증원된 파티가 있을 때") {
            `when`("새 사용자가 신청하고 호스트가 승인하면") {
                then("신청과 승인이 성공하고 참여 인원이 증가한다") {
                    val seed = seed(INITIAL_CAPACITY)
                    edit(seed, INCREASED_CAPACITY).isSuccess shouldBe true
                    requestJoin(seed).isSuccess shouldBe true
                    approve(seed).isSuccess shouldBe true
                    inspect(seed, INCREASED_CAPACITY, INITIAL_CAPACITY + 1, "New title")
                    tx.executeWithoutResult {
                        memberStatus(seed) shouldBe MemberStatus.JOINED
                    }
                }
            }
        }
        given("AT-001-3 선호도 저장 중 실패가 발생할 때") {
            `when`("게시글과 파티 필드를 수정하면") {
                then("트랜잭션 전체가 롤백된다") {
                    val linked = linkedSeed(JOINED_BEFORE_APPROVAL, INITIAL_CAPACITY)
                    val failingService = PostService(
                        posts, users, FailingCompPreferences(compPreferences), preferences, partyService, plans,
                        markers,
                        memberships, mockk(relaxed = true), provider, rooms, mockk(relaxed = true),
                            PlaceTestDoubles.resolver(), PlaceTestDoubles.regionCatalog(),
                    )
                    val change = changedRequest(linked, INCREASED_CAPACITY)
                    val failure = runAs(linked.seed.host) { failingService.updatePost(linked.seed.post, change) }
                    failure.exceptionOrNull()?.message shouldBe FORCED_FAILURE
                    inspect(linked.seed, INITIAL_CAPACITY, JOINED_BEFORE_APPROVAL, "Old title")
                    inspectPreserved(linked)
                }
            }
        }
        given("AT-002-2 AT-002-3 참여 인원보다 작은 정원이 요청되면") {
            `when`("게시글 수정을 시도하면") {
                then("정원 예외가 발생하고 게시글과 파티는 유지된다") {
                    val linked = linkedSeed(INITIAL_CAPACITY, INCREASED_CAPACITY)
                    val result = runAs(linked.seed.host) {
                        service.updatePost(linked.seed.post, changedRequest(linked, JOINED_BEFORE_APPROVAL))
                    }
                    (result.exceptionOrNull() is PartyCapacityBelowJoinedException) shouldBe true
                    inspect(linked.seed, INCREASED_CAPACITY, INITIAL_CAPACITY, "Old title")
                    inspectPreserved(linked)
                    val missingPlan = UUID.fromString("00000000-0000-0000-0000-000000000099")
                    val rejected = runAs(linked.seed.host) {
                        val change = changedRequest(linked, JOINED_BEFORE_APPROVAL, missingPlan)
                        service.updatePost(linked.seed.post, change)
                    }
                    val failure = rejected.exceptionOrNull() as? PartyCapacityBelowJoinedException
                        ?: error("capacity exception missing")
                    val details = failure.message ?: error("capacity exception message missing")
                    details shouldContain "partyUuid=${linked.seed.party}"
                    details shouldContain "capacity=$JOINED_BEFORE_APPROVAL"
                    details shouldContain "joined=$INITIAL_CAPACITY"
                    inspect(linked.seed, INCREASED_CAPACITY, INITIAL_CAPACITY, "Old title")
                    inspectPreserved(linked)
                }
            }
        }
        given("AT-003-1 AT-003-2 모집 상태를 변경할 때") {
            `when`("완료와 재개를 각각 반복하면") {
                then("상태가 멱등이고 신규 신청은 완료 후 거부 재개 후 허용된다") {
                    val seed = seed()
                    repeat(2) { status(seed, PostStatus.RECRUITMENT_COMPLETED).isSuccess shouldBe true }
                    val closed = runAs(seed.applicant) { partyService.requestJoinParty(seed.party, "Closed") }
                    (closed.exceptionOrNull() is PartyRecruitmentClosedException) shouldBe true
                    tx.executeWithoutResult {
                        parties.findByUuid(seed.party)?.recruitmentStatus shouldBe PartyRecruitmentStatus.CLOSED
                    }
                    repeat(2) { status(seed, PostStatus.RECRUITING).isSuccess shouldBe true }
                    requestJoin(seed, "Reopened").isSuccess shouldBe true
                    tx.executeWithoutResult {
                        posts.findByUuid(seed.post)?.status shouldBe PostStatus.RECRUITING
                        parties.findByUuid(seed.party)?.recruitmentStatus shouldBe PartyRecruitmentStatus.OPEN
                        memberStatus(seed) shouldBe MemberStatus.PENDING
                    }
                }
            }
        }
        given("AT-003-3 모집 완료 전 신청이 있을 때") {
            `when`("모집 완료 후 기존 신청을 승인하면") {
                then("승인이 성공하고 모집은 닫힌 상태로 유지된다") {
                    val seed = seed()
                    requestJoin(seed, "Pending").isSuccess shouldBe true
                    status(seed, PostStatus.RECRUITMENT_COMPLETED).isSuccess shouldBe true
                    approve(seed).isSuccess shouldBe true
                    tx.executeWithoutResult {
                        parties.findByUuid(seed.party)?.joined shouldBe JOINED_BEFORE_APPROVAL
                        parties.findByUuid(seed.party)?.recruitmentStatus shouldBe PartyRecruitmentStatus.CLOSED
                        memberStatus(seed) shouldBe MemberStatus.JOINED
                    }
                }
            }
            `when`("완료된 파티의 게시글 상태를 변경하면") {
                then("두 상태 요청 모두 거부된다") {
                    val seed = seed()
                    tx.executeWithoutResult { parties.findByUuid(seed.party)?.complete() }
                    listOf(PostStatus.RECRUITING, PostStatus.RECRUITMENT_COMPLETED).forEach {
                        (status(seed, it).exceptionOrNull() is InvalidInputException) shouldBe true
                    }
                    tx.executeWithoutResult {
                        parties.findByUuid(seed.party)?.progressStatus shouldBe PartyProgressStatus.COMPLETED
                    }
                }
            }
        }
    }
    companion object {
        private const val TAG_LENGTH = 8
        private const val INITIAL_JOINED = 1
        private const val JOINED_BEFORE_APPROVAL = 2
        private const val INITIAL_CAPACITY = 3
        private const val INCREASED_CAPACITY = 10
        private const val LOCATION = "Busan"
        private const val FORCED_FAILURE = "forced-comp-preference-failure"
        private val START = Instant.parse("2030-05-01T00:00:00Z")
        private val FINISH = Instant.parse("2030-05-03T00:00:00Z")
        private val NEW_START = Instant.parse("2030-06-01T00:00:00Z")
        private val NEW_FINISH = Instant.parse("2030-06-05T00:00:00Z")
    }
}
