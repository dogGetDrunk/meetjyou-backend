package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.message.ChatMessage
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.user.dto.UserDisplay
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * AT-003 to AT-006 (HTTP): chat sender, party member, join request and received application all
 * show another user through the shared display rule (#207), masked only once purged.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class WithdrawnUserDisplayIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var partyRepository: PartyRepository

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

    @Autowired
    private lateinit var userPartyRepository: UserPartyRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Value("\${local.server.port}")
    private var port: Int = 0

    private var userSeq = 0

    /** One user per status: NORMAL, DELETED (grace period), PURGED. */
    private class Trio(val active: User, val graced: User, val purged: User)

    // members.active is the host, who reads every endpoint.
    private class Scene(val party: Party, val room: ChatRoom, val members: Trio, val applicants: Trio)

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun saveUser(prefix: String): User {
        userSeq++
        val user = User(
            email = null,
            nickname = "$prefix$userSeq",
            authProvider = AuthProvider.KAKAO,
            externalId = "wd-ext-$userSeq",
        )
        user.hasProfileImage = true
        return userRepository.save(user)
    }

    private fun withdraw(user: User): User {
        user.withdraw(Instant.now())
        return userRepository.save(user)
    }

    private fun purge(user: User): User {
        user.withdraw(Instant.now())
        user.anonymize()
        return userRepository.save(user)
    }

    private fun saveMessage(room: ChatRoom, sender: User) {
        chatMessageRepository.save(ChatMessage(room = room, sender = sender, body = "hi"))
    }

    // One transaction keeps the party managed while rooms and memberships cascade onto it.
    private fun buildScene(): Scene =
        TransactionTemplate(transactionManager).execute { buildSceneInTransaction() }
            ?: error("Scene not built")

    // Everyone joins and speaks while active (CONNECT and SEND reject inactive users), then withdraws.
    private fun buildSceneInTransaction(): Scene {
        val members = listOf(saveUser("host"), saveUser("gm"), saveUser("pm"))
        val applicants = listOf(saveUser("aa"), saveUser("ga"), saveUser("pa"))
        val party = partyRepository.save(NotificationCenterFixtures.party())
        val room = chatRoomRepository.save(ChatRoom(party = party))
        userPartyRepository.save(NotificationCenterFixtures.hostUserParty(party, members[0]))
        members.drop(1).forEach { userPartyRepository.save(UserParty(party, it, PartyRole.MEMBER)) }
        applicants.forEach { userPartyRepository.save(NotificationCenterFixtures.pendingUserParty(party, it, "hi")) }
        members.forEach { saveMessage(room, it) }
        return Scene(
            party = party,
            room = room,
            members = Trio(members[0], withdraw(members[1]), purge(members[2])),
            applicants = Trio(applicants[0], withdraw(applicants[1]), purge(applicants[2])),
        )
    }

    private fun getJson(path: String, viewer: User): JsonNode {
        val headers = HttpHeaders().apply { setBearerAuth(jwtProvider.generateAccessToken(viewer.uuid, Role.USER)) }
        val response = restTemplate.exchange(url(path), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        response.statusCode.value() shouldBe HttpStatus.OK.value()
        return objectMapper.readTree(response.body)
    }

    /**
     * Checks one item per user of [trio], found by [uuidField]: the active and graced users keep their
     * nickname, the purged one is masked. [hasImage] is false for chat messages, which carry no photo flag.
     */
    private fun assertTrio(items: JsonNode, trio: Trio, uuidField: String, nicknameField: String, hasImage: Boolean) {
        val byUser = items.associateBy { it.get(uuidField).asText() }
        byUser.size shouldBe 3
        for (shown in listOf(trio.active, trio.graced)) {
            val item = byUser.getValue(shown.uuid.toString())
            item.get(nicknameField).asText() shouldBe shown.nickname
            if (hasImage) item.get("hasProfileImage").asBoolean() shouldBe true
            item.get("isWithdrawn").asBoolean() shouldBe false
        }
        val masked = byUser.getValue(trio.purged.uuid.toString())
        masked.get(nicknameField).asText() shouldBe UserDisplay.WITHDRAWN_NICKNAME
        if (hasImage) masked.get("hasProfileImage").asBoolean() shouldBe false
        masked.get("isWithdrawn").asBoolean() shouldBe true
    }

    init {
        extensions(SpringExtension())

        afterEach {
            chatMessageRepository.deleteAll()
            userPartyRepository.deleteAll()
            chatRoomRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
        }

        given("호스트가 정상, 탈퇴 유예 중, 완전 탈퇴 회원이 섞인 파티를 조회할 때") {
            `when`("채팅 메시지 이력을 읽으면") {
                then("완전 탈퇴한 발신자만 탈퇴 문구와 isWithdrawn=true로 내려온다") {
                    val scene = buildScene()

                    val body = getJson("/chat/rooms/${scene.room.uuid}/messages", scene.members.active)

                    assertTrio(body.get("messages"), scene.members, "senderUuid", "senderNickname", hasImage = false)
                }
            }

            `when`("파티 멤버 목록을 읽으면") {
                then("완전 탈퇴한 멤버만 가려지고 호스트와 유예 중 멤버는 그대로다") {
                    val scene = buildScene()

                    val body = getJson("/parties/${scene.party.uuid}/members", scene.members.active)

                    assertTrio(body, scene.members, "userUuid", "nickname", hasImage = true)
                }
            }

            `when`("가입 신청 목록을 읽으면") {
                then("완전 탈퇴한 신청자만 가려진다") {
                    val scene = buildScene()

                    val body = getJson("/parties/${scene.party.uuid}/join-requests", scene.members.active)

                    assertTrio(body.get("requests"), scene.applicants, "userUuid", "nickname", hasImage = true)
                }
            }

            `when`("알림센터 받은 신청을 읽으면") {
                then("완전 탈퇴한 신청자만 가려진다") {
                    val scene = buildScene()

                    val body = getJson("/notification-center/received-applications", scene.members.active)

                    assertTrio(body.get("applications"), scene.applicants, "userUuid", "nickname", hasImage = true)
                }
            }
        }
    }
}
