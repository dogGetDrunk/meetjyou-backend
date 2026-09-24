package com.dogGetDrunk.meetjyou.party

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * End-to-end check of PATCH /parties/{uuid} (rename — the only editable party attribute besides
 * the image) through the real security filter chain, bean validation and JPA. Also guards that
 * the removed bulk-update PUT stays removed.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class UpdatePartyNameIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var userPartyRepository: UserPartyRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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

    private fun tokenOf(user: User) = jwtProvider.generateAccessToken(user.uuid, user.email, Role.USER)

    // Trip already started: renaming must not be tied to itinerary validation.
    // Runs in one transaction because ChatRoom's @MapsId needs the Party to still be managed.
    private fun createStartedParty(host: User): Party = inTransaction {
        val now = Instant.now()
        val party = partyRepository.save(
            Party(
                itinStart = now.minus(1, ChronoUnit.DAYS),
                itinFinish = now.plus(2, ChronoUnit.DAYS),
                destination = "Busan",
                joined = 1,
                capacity = 4,
                name = "Old name",
            )
        )
        userPartyRepository.save(UserParty(party, host, PartyRole.HOST))
        chatRoomRepository.save(ChatRoom(party = party))
        party
    }

    private fun <T> inTransaction(block: () -> T): T =
        TransactionTemplate(transactionManager).execute { block() } ?: error("Empty transaction result")

    private fun exchange(method: HttpMethod, path: String, body: Any?, token: String): Pair<HttpStatus, String?> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }
        val response = restTemplate.exchange(
            "http://localhost:$port/api/v1$path",
            method,
            HttpEntity(body, headers),
            String::class.java,
        )
        return HttpStatus.valueOf(response.statusCode.value()) to response.body
    }

    private fun patchName(partyUuid: UUID, name: String, token: String) =
        exchange(HttpMethod.PATCH, "/parties/$partyUuid", mapOf("name" to name), token)

    private fun storedName(partyUuid: UUID) = partyRepository.findByUuid(partyUuid)?.name

    private fun cleanup() = inTransaction {
        userPartyRepository.deleteAll()
        chatRoomRepository.deleteAll()
        partyRepository.deleteAll()
        userRepository.deleteAll()
    }

    init {
        extensions(SpringExtension())

        // The default SimpleClientHttpRequestFactory (HttpURLConnection) cannot send PATCH.
        beforeSpec { restTemplate.restTemplate.requestFactory = JdkClientHttpRequestFactory() }
        afterEach { cleanup() }

        given("여행이 이미 시작된 파티에서") {
            `when`("HOST가 제거된 전체 수정 PUT을 호출하면") {
                then("405이고 파티는 바뀌지 않는다") {
                    val host = createUser("put-host")
                    val party = createStartedParty(host)
                    val body = mapOf(
                        "itinStart" to party.itinStart.plus(1, ChronoUnit.DAYS).toString(),
                        "itinFinish" to party.itinFinish.plus(1, ChronoUnit.DAYS).toString(),
                        "destination" to "Jeju",
                        "capacity" to party.capacity + 1,
                        "name" to "New name",
                        "planUuid" to null,
                    )

                    val (status, _) = exchange(HttpMethod.PUT, "/parties/${party.uuid}", body, tokenOf(host))

                    status shouldBe HttpStatus.METHOD_NOT_ALLOWED
                    val stored = partyRepository.findByUuid(party.uuid)
                    stored?.name shouldBe "Old name"
                    stored?.destination shouldBe "Busan"
                    stored?.capacity shouldBe party.capacity
                }
            }

            `when`("HOST가 PATCH로 이름을 바꾸면") {
                then("200과 새 이름을 반환하고 DB와 채팅방 목록에 반영된다") {
                    val host = createUser("patch-host")
                    val party = createStartedParty(host)
                    val token = tokenOf(host)

                    val (status, body) = patchName(party.uuid, "New name", token)

                    status shouldBe HttpStatus.OK
                    objectMapper.readTree(body).get("name").asText() shouldBe "New name"
                    storedName(party.uuid) shouldBe "New name"

                    val (roomsStatus, roomsBody) = exchange(HttpMethod.GET, "/chat/rooms", null, token)
                    roomsStatus shouldBe HttpStatus.OK
                    objectMapper.readTree(roomsBody).get("rooms").get(0).get("partyName").asText() shouldBe "New name"
                }
            }

            `when`("파티에 참여하지 않은 유저가 PATCH하면") {
                then("403이고 이름은 바뀌지 않는다") {
                    val host = createUser("owner")
                    val stranger = createUser("stranger")
                    val party = createStartedParty(host)

                    val (status, _) = patchName(party.uuid, "Hijacked", tokenOf(stranger))

                    status shouldBe HttpStatus.FORBIDDEN
                    storedName(party.uuid) shouldBe "Old name"
                }
            }

            `when`("이름이 공백이거나 최대 길이를 넘으면") {
                then("400이고 이름은 바뀌지 않는다") {
                    val host = createUser("invalid-host")
                    val party = createStartedParty(host)
                    val token = tokenOf(host)

                    patchName(party.uuid, "   ", token).first shouldBe HttpStatus.BAD_REQUEST
                    patchName(party.uuid, "a".repeat(21), token).first shouldBe HttpStatus.BAD_REQUEST
                    storedName(party.uuid) shouldBe "Old name"
                }
            }

            `when`("이름이 최대 길이(20자)와 같으면") {
                then("200이다") {
                    val host = createUser("boundary-host")
                    val party = createStartedParty(host)
                    val name = "a".repeat(20)

                    patchName(party.uuid, name, tokenOf(host)).first shouldBe HttpStatus.OK
                    storedName(party.uuid) shouldBe name
                }
            }
        }
    }
}
