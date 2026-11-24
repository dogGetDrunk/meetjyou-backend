package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.support.ChatTestDataHelper
import com.dogGetDrunk.meetjyou.chat.support.RawStompClient
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Guards #294, #295 and the STOMP alias: frames a well-behaved app never sends must not get past
 * ChatStompInterceptor. A SEND to a broker destination would skip ChatService's membership check and
 * reach every subscriber; a second CONNECT (or STOMP) would rebind the connection to another room
 * so a kick from the first room no longer closes it.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class ChatStompFrameGuardIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var chatTestDataHelper: ChatTestDataHelper

    @Autowired
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val clients = mutableListOf<RawStompClient>()

    private data class Target(
        val name: String,
        val destination: (UUID) -> String,
    )

    private val targets = listOf(
        Target(name = "AT-003-1 다른 방 메시지 목적지", destination = { room -> "/sub/chat/room/$room" }),
        Target(name = "AT-003-2 다른 방 이벤트 목적지", destination = { room -> "/sub/chat/room/$room/events" }),
    )

    init {
        extensions(SpringExtension())

        afterEach {
            clients.forEach { it.close() }
            clients.clear()
            chatTestDataHelper.cleanup()
        }

        given("강퇴된 방 주소를 아는 공격자가 자기 방에 정상 연결한 상태") {
            targets.forEach { target ->
                `when`("${target.name}로 직접 SEND하면") {
                    then("공격자는 ERROR와 연결 종료, 이미 수신 중인 피해자는 그 프레임을 받지 않는다") {
                        verifyBrokerSendRejected(target)
                    }
                }
            }
            // Name-based user routing finds no session today (SimpUserRegistry stays empty, see the
            // task's logs/red-observations.md), so no victim can be shown receiving it; the guard
            // must still refuse it in case that registration is ever fixed.
            `when`("AT-003-3 다른 유저 오류 큐(/user/{uuid}/queue/errors)로 직접 SEND하면") {
                then("공격자는 ERROR와 연결 종료") {
                    val victim = chatTestDataHelper.createTestData()
                    val attackerUuid = chatTestDataHelper.addMember(victim.roomUuid, "attacker", MemberStatus.BANNED)
                    val attacker = connected(chatTestDataHelper.addRoomFor(attackerUuid), attackerUuid)

                    attacker.sendTo("/user/${victim.userUuid}${ChatController.ERROR_QUEUE}", forgedBody())

                    assertRejected(attacker, ErrorCode.INVALID_INPUT_VALUE)
                }
            }
        }

        given("방 A에 연결된 멤버가 방 B에도 속한 상태") {
            `when`("AT-004-1 같은 소켓으로 방 B CONNECT를 다시 보내면") {
                then("ERROR(INVALID_INPUT_VALUE)와 연결 종료") {
                    verifyDuplicateRejected(RawStompClient.CONNECT, RawStompClient.CONNECT)
                }
            }
            `when`("AT-005-3 CONNECT 뒤에 STOMP를 보내면") {
                then("ERROR(INVALID_INPUT_VALUE)와 연결 종료") {
                    verifyDuplicateRejected(RawStompClient.CONNECT, RawStompClient.STOMP)
                }
            }
            `when`("AT-005-3 STOMP 뒤에 CONNECT를 보내면") {
                then("ERROR(INVALID_INPUT_VALUE)와 연결 종료") {
                    verifyDuplicateRejected(RawStompClient.STOMP, RawStompClient.CONNECT)
                }
            }
        }

        given("STOMP 명령(CONNECT 별칭)으로 연결할 때") {
            `when`("AT-005-1 토큰이 없으면") {
                then("ERROR(UNAUTHENTICATED)와 연결 종료") {
                    val data = chatTestDataHelper.createTestData()
                    val client = open()
                    client.connect(data.roomUuid, token = null, command = RawStompClient.STOMP)

                    assertRejected(client, ErrorCode.UNAUTHENTICATED)
                }
            }
            `when`("AT-005-2 유효한 토큰과 멤버인 방이면") {
                then("CONNECTED를 받고 연결이 유지된다") {
                    val data = chatTestDataHelper.createTestData()
                    val client = open()
                    client.connect(data.roomUuid, tokenOf(data.userUuid), RawStompClient.STOMP)

                    client.awaitFrame("CONNECTED").shouldNotBeNull()
                    client.isOpen() shouldBe true
                }
            }
        }
    }

    private fun verifyBrokerSendRejected(target: Target) {
        val victim = chatTestDataHelper.createTestData()
        val attackerUuid = chatTestDataHelper.addMember(victim.roomUuid, "attacker", MemberStatus.BANNED)
        val attackerRoomUuid = chatTestDataHelper.addRoomFor(attackerUuid)

        val victimClient = connected(victim.roomUuid, victim.userUuid)
        val destination = target.destination(victim.roomUuid)
        victimClient.subscribe(destination)
        awaitSubscriptionReady(victimClient) { body -> messagingTemplate.convertAndSend(destination, body) }

        val attacker = connected(attackerRoomUuid, attackerUuid)
        attacker.sendTo(destination, forgedBody())

        assertRejected(attacker, ErrorCode.INVALID_INPUT_VALUE)
        victimClient.awaitContaining(FORGED, SILENCE_MILLIS).shouldBeNull()
    }

    private fun verifyDuplicateRejected(firstCommand: String, secondCommand: String) {
        val data = chatTestDataHelper.createTestData()
        val otherRoomUuid = chatTestDataHelper.addRoomFor(data.userUuid)
        val client = open()
        client.connect(data.roomUuid, tokenOf(data.userUuid), firstCommand)
        client.awaitFrame("CONNECTED").shouldNotBeNull()

        client.connect(otherRoomUuid, tokenOf(data.userUuid), secondCommand)

        assertRejected(client, ErrorCode.INVALID_INPUT_VALUE)
    }

    // A control frame through the server's own send path proves the subscription is registered and
    // routable, so the later silence means the forged frame was dropped, not that it arrived early.
    private fun awaitSubscriptionReady(client: RawStompClient, sendControl: (Map<String, String>) -> Unit) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            sendControl(mapOf("marker" to CONTROL))
            if (client.awaitContaining(CONTROL, CONTROL_RETRY_MILLIS) != null) return
        }
        error("subscription never became routable")
    }

    private fun assertRejected(client: RawStompClient, errorCode: ErrorCode) {
        val error = client.awaitFrame("ERROR").shouldNotBeNull()
        error shouldContain "\nmessage:${errorCode.name}\n"
        client.closeStatus.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).shouldNotBeNull()
    }

    private fun connected(roomUuid: UUID, userUuid: UUID): RawStompClient {
        val client = open()
        client.connect(roomUuid, tokenOf(userUuid))
        client.awaitFrame("CONNECTED").shouldNotBeNull()
        return client
    }

    private fun open(): RawStompClient {
        val client = RawStompClient.open(port)
        clients.add(client)
        return client
    }

    private fun forgedBody() = """{"marker":"$FORGED"}"""

    private fun tokenOf(userUuid: UUID) = jwtProvider.generateAccessToken(userUuid, Role.USER)

    companion object {
        private const val CONTROL = "control-frame"
        private const val FORGED = "forged-frame"
        private const val TIMEOUT_MILLIS = 5000L
        private const val CONTROL_RETRY_MILLIS = 300L
        private const val SILENCE_MILLIS = 1000L
    }
}
