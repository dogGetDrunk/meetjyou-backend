package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.party.PartyCapacityBelowJoinedException
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
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
            provider)
    }
    private val service by lazy {
        PostService(posts, users, compPreferences, preferences, partyService, plans, markers, memberships,
            mockk(relaxed = true), provider, rooms, mockk(relaxed = true))
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
                    val seed = seed(JOINED_BEFORE_APPROVAL, INITIAL_CAPACITY)
                    // Spring Data repositories are JDK proxies that mockk can't spy on; a relaxed mock
                    // fails the save step instead, and the Post/Party rollback is what this case checks.
                    val failingRepository = mockk<CompPreferenceRepository>(relaxed = true)
                    val preferenceRepository = mockk<PreferenceRepository>()
                    val preference = mockk<Preference>()
                    val companion = mockk<CompanionSpec>()
                    val type = PreferenceType.entries.first()
                    every { companion.formalizeTypesToNames() } returns mapOf(type to listOf("forced"))
                    every { preferenceRepository.findAllByTypeAndNameIn(type, listOf("forced")) } returns
                        listOf(preference)
                    every { preference.name } returns "forced"
                    every { preference.id } returns 1L
                    every { failingRepository.saveAll(any<List<CompPreference>>()) } throws
                        RuntimeException(FORCED_FAILURE)
                    val failingService = PostService(
                        posts, users, failingRepository, preferenceRepository, partyService, plans, markers,
                        memberships, mockk(relaxed = true), provider, rooms, mockk(relaxed = true),
                    )
                    val change = request(INCREASED_CAPACITY).copy(companionSpec = companion, location = "Jeju")
                    val failure = runAs(seed.host) { failingService.updatePost(seed.post, change) }
                    failure.exceptionOrNull()?.message shouldBe FORCED_FAILURE
                    inspect(seed, INITIAL_CAPACITY, JOINED_BEFORE_APPROVAL, "Old title")
                    tx.executeWithoutResult {
                        posts.findByUuid(seed.post)?.location shouldBe LOCATION
                        parties.findByUuid(seed.party)?.destination shouldBe LOCATION
                    }
                }
            }
        }
        given("AT-002-2 AT-002-3 참여 인원보다 작은 정원이 요청되면") {
            `when`("게시글 수정을 시도하면") {
                then("정원 예외가 발생하고 게시글과 파티는 유지된다") {
                    val seed = seed(INITIAL_CAPACITY, INCREASED_CAPACITY)
                    val result = edit(seed, JOINED_BEFORE_APPROVAL)
                    (result.exceptionOrNull() is PartyCapacityBelowJoinedException) shouldBe true
                    inspect(seed, INCREASED_CAPACITY, INITIAL_CAPACITY, "Old title")
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
