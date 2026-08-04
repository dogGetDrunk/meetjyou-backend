package com.dogGetDrunk.meetjyou.post

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.common.idempotency.IdempotencyKeyRepository
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.post.dto.CreatePostResponse
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves the DB unique constraint (not just the mockk-level unit tests) is the real backstop for
 * concurrent double-submits, and that PostController's conflict-recovery path re-validates the
 * request hash instead of blindly returning whichever request committed first.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class PostIdempotencyIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var postRepository: PostRepository

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var userPartyRepository: UserPartyRepository

    @Autowired
    private lateinit var idempotencyKeyRepository: IdempotencyKeyRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${local.server.port}")
    private var port: Int = 0

    @MockBean
    private lateinit var firebaseApp: FirebaseApp

    @MockBean
    private lateinit var ociAuthProvider: AuthenticationDetailsProvider

    @MockBean
    private lateinit var objectStorageClient: ObjectStorageClient

    @MockBean
    private lateinit var workRequestClient: WorkRequestClient

    private fun createTestUser(email: String): User = userRepository.save(
        User(email = email, nickname = email.substringBefore("@"), authProvider = AuthProvider.KAKAO, externalId = email)
    )

    private fun samplePostRequest(title: String) = CreatePostRequest(
        title = title,
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

    // Takes a pre-built request rather than building one per call: two threads each calling
    // Instant.now() independently would drift by nanoseconds, making even a "same body" concurrent
    // test send two byte-different bodies and defeating the point of the test.
    private fun postRequest(request: CreatePostRequest, idempotencyKey: String?, token: String): ResponseEntityResult {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
            idempotencyKey?.let { set("Idempotency-Key", it) }
        }
        // Read the body as raw String rather than CreatePostResponse::class.java: on the 409 path
        // the body is an ErrorResponse (no "uuid" field), and letting the message converter force
        // it into CreatePostResponse blows up with MissingKotlinParameterException before this
        // method can even inspect the status code.
        val response = restTemplate.postForEntity(
            "http://localhost:$port/api/v1/posts",
            HttpEntity(request, headers),
            String::class.java,
        )
        val status = HttpStatus.valueOf(response.statusCode.value())
        val body = if (status == HttpStatus.OK) objectMapper.readValue(response.body, CreatePostResponse::class.java) else null
        return ResponseEntityResult(status, body)
    }

    private data class ResponseEntityResult(val status: HttpStatus, val body: CreatePostResponse?)

    @Transactional
    fun cleanup() {
        idempotencyKeyRepository.deleteAll()
        postRepository.deleteAll()
        userPartyRepository.deleteAll()
        chatRoomRepository.deleteAll()
        partyRepository.deleteAll()
        userRepository.deleteAll()
    }

    init {
        extensions(SpringExtension())

        afterEach { cleanup() }

        given("동일한 Idempotency-Key와 동일한 바디로 두 요청이 동시에 들어오면") {
            `when`("둘 다 사전 조회를 통과해 생성 경로로 진입해도") {
                then("Post row는 정확히 1개만 생성되고 둘 다 같은 uuid로 200을 받는다") {
                    val user = createTestUser("idem-same-${System.nanoTime()}@example.com")
                    val token = jwtProvider.generateAccessToken(user.uuid, user.email, Role.USER)
                    val key = "same-body-key-${System.nanoTime()}"
                    val request = samplePostRequest("Same Body")

                    val latch = CountDownLatch(2)
                    val executor = Executors.newFixedThreadPool(2)
                    val results = List(2) {
                        executor.submit<ResponseEntityResult> {
                            latch.countDown()
                            latch.await(5, TimeUnit.SECONDS)
                            postRequest(request, key, token)
                        }
                    }.map { it.get(10, TimeUnit.SECONDS) }
                    executor.shutdown()

                    results.forEach { it.status shouldBe HttpStatus.OK }
                    results.map { it.body?.uuid }.distinct().size shouldBe 1
                    postRepository.findAllByAuthorUuidWithAuthor(user.uuid, org.springframework.data.domain.Pageable.unpaged())
                        .content.size shouldBe 1
                }
            }
        }

        given("동일한 Idempotency-Key로 서로 다른 바디의 두 요청이 동시에 들어오면") {
            `when`("하나는 유니크 제약에 걸려 롤백되면") {
                then("이긴 쪽은 200, 진 쪽은 컨트롤러의 해시 재검증에 의해 409를 받는다") {
                    val user = createTestUser("idem-diff-${System.nanoTime()}@example.com")
                    val token = jwtProvider.generateAccessToken(user.uuid, user.email, Role.USER)
                    val key = "diff-body-key-${System.nanoTime()}"
                    val requestA = samplePostRequest("Body A")
                    val requestB = samplePostRequest("Body B")

                    val latch = CountDownLatch(2)
                    val executor = Executors.newFixedThreadPool(2)
                    val results = listOf(
                        executor.submit<ResponseEntityResult> {
                            latch.countDown()
                            latch.await(5, TimeUnit.SECONDS)
                            postRequest(requestA, key, token)
                        },
                        executor.submit<ResponseEntityResult> {
                            latch.countDown()
                            latch.await(5, TimeUnit.SECONDS)
                            postRequest(requestB, key, token)
                        },
                    ).map { it.get(10, TimeUnit.SECONDS) }
                    executor.shutdown()

                    val statuses = results.map { it.status }
                    statuses.count { it == HttpStatus.OK } shouldBe 1
                    statuses.count { it == HttpStatus.CONFLICT } shouldBe 1
                    postRepository.findAllByAuthorUuidWithAuthor(user.uuid, org.springframework.data.domain.Pageable.unpaged())
                        .content.size shouldBe 1
                }
            }
        }
    }
}
