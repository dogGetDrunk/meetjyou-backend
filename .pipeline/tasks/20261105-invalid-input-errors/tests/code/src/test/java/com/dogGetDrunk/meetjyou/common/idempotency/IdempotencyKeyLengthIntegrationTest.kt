package com.dogGetDrunk.meetjyou.common.idempotency

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.plan.MarkerRepository
import com.dogGetDrunk.meetjyou.plan.PlanRepository
import com.dogGetDrunk.meetjyou.plan.dto.CreateMarkerRequest
import com.dogGetDrunk.meetjyou.plan.dto.CreatePlanRequest
import com.dogGetDrunk.meetjyou.post.PostRepository
import com.dogGetDrunk.meetjyou.post.dto.CreatePostRequest
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * AT-002-2: the real post / plan create APIs against the real service and DB.
 * - 256-char Idempotency-Key -> 400 INVALID_INPUT_VALUE, nothing stored (no resource, no related rows, no key row)
 * - 255-char key -> created once, an identical retry returns the same resource without duplicating anything
 * - null key -> created normally, no idempotency key row
 *
 * Note: the create endpoints currently answer 200 (no @ResponseStatus), while the spec text says 201,
 * so success is asserted as "any 2xx" to keep the status-code question out of this key-length test.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class IdempotencyKeyLengthIntegrationTest : BehaviorSpec() {

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
    private lateinit var planRepository: PlanRepository

    @Autowired
    private lateinit var markerRepository: MarkerRepository

    @Autowired
    private lateinit var idempotencyKeyRepository: IdempotencyKeyRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${local.server.port}")
    private var port: Int = 0

    private enum class Kind(val path: String) {
        POST("/api/v1/posts"),
        PLAN("/api/v1/plans"),
    }

    private data class ApiResult(val status: HttpStatus, val json: JsonNode)

    private data class Snapshot(
        val posts: Long,
        val plans: Long,
        val markers: Long,
        val parties: Long,
        val chatRooms: Long,
        val keys: Long,
    ) {
        fun resources(kind: Kind): Long = if (kind == Kind.POST) posts else plans
    }

    private fun snapshot() = Snapshot(
        posts = postRepository.count(),
        plans = planRepository.count(),
        markers = markerRepository.count(),
        parties = partyRepository.count(),
        chatRooms = chatRoomRepository.count(),
        keys = idempotencyKeyRepository.count(),
    )

    private fun tokenOfNewUser(): String {
        val suffix = UUID.randomUUID().toString()
        val user = userRepository.save(
            User(
                email = "$suffix@example.com",
                nickname = suffix.take(NICKNAME_LENGTH),
                authProvider = AuthProvider.KAKAO,
                externalId = suffix,
            )
        )
        return jwtProvider.generateAccessToken(user.uuid, Role.USER)
    }

    // Built once per scenario and reused for retries so the serialized body (and its hash) stays identical.
    private fun bodyOf(kind: Kind): Any {
        val start = Instant.now().plusSeconds(HOUR_SECONDS)
        val finish = start.plusSeconds(HOUR_SECONDS)
        return if (kind == Kind.POST) {
            CreatePostRequest(
                title = "Key length",
                content = "content",
                isInstant = false,
                itinStart = start,
                itinFinish = finish,
                location = "Seoul",
                capacity = POST_CAPACITY,
                companionSpec = null,
                planUuid = null,
                isPlanPublic = null,
            )
        } else {
            CreatePlanRequest(
                title = "Key length",
                itinStart = start,
                itinFinish = finish,
                location = "Seoul",
                centerLat = SEOUL_LAT,
                centerLng = SEOUL_LNG,
                memo = null,
                markers = listOf(CreateMarkerRequest(SEOUL_LAT, SEOUL_LNG, start, 1, 0, "Gyeongbokgung", null)),
            )
        }
    }

    private fun send(kind: Kind, body: Any, key: String?, token: String): ApiResult {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
            key?.let { set(IDEMPOTENCY_HEADER, it) }
        }
        // Raw String body: error responses are ErrorResponse JSON, not the create response type.
        val url = "http://localhost:$port${kind.path}"
        val response = restTemplate.postForEntity(url, HttpEntity(body, headers), String::class.java)
        val status = HttpStatus.valueOf(response.statusCode.value())
        return ApiResult(status, objectMapper.readTree(response.body ?: EMPTY_JSON))
    }

    private fun cleanup() {
        idempotencyKeyRepository.deleteAll()
        markerRepository.deleteAll()
        postRepository.deleteAll()
        userPartyRepository.deleteAll()
        chatRoomRepository.deleteAll()
        partyRepository.deleteAll()
        planRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun rejects256(kind: Kind) {
        val token = tokenOfNewUser()
        val before = snapshot()

        val result = send(kind, bodyOf(kind), "k".repeat(KEY_TOO_LONG), token)

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.json["errorCode"].asText() shouldBe "INVALID_INPUT_VALUE"
        snapshot() shouldBe before
    }

    private fun createsOnceFor255(kind: Kind) {
        val token = tokenOfNewUser()
        val body = bodyOf(kind)
        val key = "k".repeat(KEY_MAX)
        val before = snapshot()

        val first = send(kind, body, key, token)
        val afterFirst = snapshot()
        val retry = send(kind, body, key, token)

        first.status.is2xxSuccessful shouldBe true
        retry.status.is2xxSuccessful shouldBe true
        retry.json["uuid"].asText() shouldBe first.json["uuid"].asText()
        afterFirst.resources(kind) shouldBe before.resources(kind) + 1
        afterFirst.keys shouldBe before.keys + 1
        snapshot() shouldBe afterFirst
    }

    private fun createsWithoutKey(kind: Kind) {
        val token = tokenOfNewUser()
        val before = snapshot()

        val result = send(kind, bodyOf(kind), null, token)

        result.status.is2xxSuccessful shouldBe true
        snapshot().resources(kind) shouldBe before.resources(kind) + 1
        snapshot().keys shouldBe before.keys
    }

    init {
        extensions(SpringExtension())

        afterEach { cleanup() }

        Kind.entries.forEach { kind ->
            given("AT-002-2 ${kind.name} 생성 API에 256자 Idempotency-Key를 보내면") {
                `when`("유효한 인증과 요청으로 호출하면") {
                    then("400 INVALID_INPUT_VALUE(404 아님)이고 리소스·연관 행·키 행이 하나도 늘지 않는다") {
                        rejects256(kind)
                    }
                }
            }

            given("AT-002-2 ${kind.name} 생성 API에 255자 Idempotency-Key를 보내면") {
                `when`("최초 생성 후 같은 키·같은 바디로 재요청하면") {
                    then("최초는 성공, 재요청은 같은 UUID를 돌려주고 중복 저장이 없다") {
                        createsOnceFor255(kind)
                    }
                }
            }

            given("AT-002-2 ${kind.name} 생성 API에 Idempotency-Key가 없으면") {
                `when`("호출하면") {
                    then("정상 생성되고 멱등 키 행은 만들어지지 않는다") {
                        createsWithoutKey(kind)
                    }
                }
            }
        }
    }

    private companion object {
        const val IDEMPOTENCY_HEADER = "Idempotency-Key"
        const val EMPTY_JSON = "{}"
        const val KEY_MAX = 255
        const val KEY_TOO_LONG = 256
        const val NICKNAME_LENGTH = 10
        const val HOUR_SECONDS = 3600L
        const val POST_CAPACITY = 4
        const val SEOUL_LAT = 37.5665
        const val SEOUL_LNG = 126.9780
    }
}
