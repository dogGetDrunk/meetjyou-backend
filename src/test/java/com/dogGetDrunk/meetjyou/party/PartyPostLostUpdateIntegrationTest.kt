package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.post.Post
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.PostStatus
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Guards issue #144: a transaction that loaded Party/Post without a lock must not revert columns it
 * never changed (`@DynamicUpdate`), and a post status change racing a party completion must be
 * serialized on the party row lock so a completed party's post never reads as recruiting.
 *
 * Races are reproduced deterministically: the "concurrent" write runs in a REQUIRES_NEW transaction
 * (its own persistence context and connection) between the stale read and the stale flush.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class PartyPostLostUpdateIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var userPartyRepository: UserPartyRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Value("\${local.server.port}")
    private var port: Int = 0

    private fun createUser(prefix: String): User {
        val email = "$prefix-${System.nanoTime()}@example.com"
        return userRepository.save(
            User(
                email = email,
                nickname = email.substringBefore("@"),
                authProvider = AuthProvider.KAKAO,
                externalId = email,
            )
        )
    }

    private fun createPartyWithPost(host: User): Pair<UUID, UUID> = inTransaction {
        val now = Instant.now()
        val party = partyRepository.save(
            Party(
                itinStart = now.plus(1, ChronoUnit.DAYS),
                itinFinish = now.plus(INITIAL_TRIP_DAYS, ChronoUnit.DAYS),
                destination = "Busan",
                joined = INITIAL_JOINED,
                capacity = CAPACITY,
                name = "Party",
            )
        )
        userPartyRepository.save(UserParty(party, host, PartyRole.HOST))
        val post = Post(
            party = party,
            isInstant = false,
            title = "Old title",
            content = "content",
            itinStart = party.itinStart,
            itinFinish = party.itinFinish,
            location = "Busan",
            capacity = CAPACITY,
        )
        post.author = host
        postRepository.save(post)
        party.uuid to post.uuid
    }

    private fun <T> inTransaction(block: () -> T): T =
        TransactionTemplate(transactionManager).execute { block() } ?: error("Empty transaction result")

    private fun <T> inNewTransaction(block: () -> T): T {
        val template = TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
        return template.execute { block() } ?: error("Empty transaction result")
    }

    private fun patchPostStatus(postUuid: UUID, status: PostStatus, user: User): HttpStatus {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(jwtProvider.generateAccessToken(user.uuid, user.email, Role.USER))
        val response = restTemplate.exchange(
            "http://localhost:$port/api/v1/posts/$postUuid/status",
            HttpMethod.PATCH,
            HttpEntity(mapOf("status" to status.name), headers),
            String::class.java,
        )
        return HttpStatus.valueOf(response.statusCode.value())
    }

    private fun cleanup() = inTransaction {
        postRepository.deleteAll()
        userPartyRepository.deleteAll()
        partyRepository.deleteAll()
        userRepository.deleteAll()
    }

    init {
        extensions(SpringExtension())

        // The default SimpleClientHttpRequestFactory (HttpURLConnection) cannot send PATCH.
        beforeSpec { restTemplate.restTemplate.requestFactory = JdkClientHttpRequestFactory() }
        afterEach { cleanup() }

        given("잠금 없이 Party를 읽은 트랜잭션이 있을 때") {
            `when`("그 사이 다른 트랜잭션이 joined를 바꿔 커밋하고, 원 트랜잭션은 imageState만 바꿔 커밋하면") {
                then("joined는 덮어써지지 않고 두 변경이 모두 남는다") {
                    val (partyUuid, _) = createPartyWithPost(createUser("joined-host"))

                    inTransaction {
                        val stale = partyRepository.findByUuid(partyUuid) ?: error("party missing")
                        inNewTransaction {
                            val fresh = partyRepository.findByUuid(partyUuid) ?: error("party missing")
                            fresh.joined = CONCURRENT_JOINED
                        }
                        stale.imageState = PartyImageState.CUSTOM
                    }

                    val stored = partyRepository.findByUuid(partyUuid) ?: error("party missing")
                    stored.joined shouldBe CONCURRENT_JOINED
                    stored.imageState shouldBe PartyImageState.CUSTOM
                }
            }
        }

        given("잠금 없이 Post를 읽은 트랜잭션이 있을 때") {
            `when`("그 사이 파티 완료로 status가 모집 완료로 커밋되고, 원 트랜잭션은 제목만 바꿔 커밋하면") {
                then("status는 모집 완료로 남는다") {
                    val (_, postUuid) = createPartyWithPost(createUser("status-host"))

                    inTransaction {
                        val stale = postRepository.findByUuid(postUuid) ?: error("post missing")
                        inNewTransaction {
                            val fresh = postRepository.findByUuid(postUuid) ?: error("post missing")
                            fresh.completeRecruitment()
                        }
                        stale.title = "New title"
                    }

                    val stored = postRepository.findByUuid(postUuid) ?: error("post missing")
                    stored.status shouldBe PostStatus.RECRUITMENT_COMPLETED
                    stored.title shouldBe "New title"
                }
            }
        }

        given("파티 완료 트랜잭션이 파티 행 잠금을 쥐고 있을 때") {
            `when`("호스트가 게시글 상태를 모집 중으로 바꾸는 요청을 보내면") {
                then("잠금이 풀릴 때까지 기다린 뒤 완료된 파티를 보고 400으로 거절되고, 게시글은 모집 완료로 남는다") {
                    val host = createUser("lock-host")
                    val (partyUuid, postUuid) = createPartyWithPost(host)
                    // Warm-up through the same path (a no-op: already RECRUITING) so first-request
                    // initialization doesn't push the racing request past the lock hold window.
                    patchPostStatus(postUuid, PostStatus.RECRUITING, host) shouldBe HttpStatus.OK
                    val locked = CountDownLatch(1)

                    val completion = CompletableFuture.runAsync {
                        inTransaction {
                            val party = partyRepository.findByUuidForUpdate(partyUuid) ?: error("party missing")
                            locked.countDown()
                            Thread.sleep(LOCK_HOLD_MILLIS)
                            party.complete()
                            postRepository.findByParty_Uuid(partyUuid)?.completeRecruitment()
                        }
                    }
                    locked.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true

                    val status = patchPostStatus(postUuid, PostStatus.RECRUITING, host)
                    completion.get(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)

                    status shouldBe HttpStatus.BAD_REQUEST
                    postRepository.findByUuid(postUuid)?.status shouldBe PostStatus.RECRUITMENT_COMPLETED
                }
            }
        }
    }

    companion object {
        private const val INITIAL_JOINED = 1
        private const val CONCURRENT_JOINED = 2
        private const val CAPACITY = 4
        private const val INITIAL_TRIP_DAYS = 3L

        // Long enough for the unlocked (pre-fix) request to finish while the lock is held, and
        // well under H2's default 1s lock timeout so the fixed request waits instead of failing.
        private const val LOCK_HOLD_MILLIS = 600L
        private const val LATCH_TIMEOUT_SECONDS = 10L
    }
}
