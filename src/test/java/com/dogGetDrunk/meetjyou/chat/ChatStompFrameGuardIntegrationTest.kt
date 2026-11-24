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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Guards #294, #295 and the STOMP alias: frames a well-behaved app never sends must not get past
 * ChatStompInterceptor. A SEND to a broker destination would skip ChatService's membership check and
 * reach every subscriber; a second CONNECT (or STOMP) would rebind the connection to another room
 * so a kick from the first room no longer closes it.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Import(ChatStompFrameGuardIntegrationTest.RecorderConfig::class)
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

    @Autowired
    private lateinit var subscriptions: SubscriptionRecorder

    private val clients = mutableListOf<RawStompClient>()

    private data class Victim(val userUuid: UUID, val roomUuid: UUID)

    private data class Target(
        val name: String,
        val subscription: (Victim) -> String,
        val attackDestination: (Victim) -> String,
        val sendControl: (Victim, Map<String, String>) -> Unit,
    )

    private val targets = listOf(
        Target(
            name = "AT-003-1 다른 방 메시지 목적지",
            subscription = { victim -> "/sub/chat/room/${victim.roomUuid}" },
            attackDestination = { victim -> "/sub/chat/room/${victim.roomUuid}" },
            sendControl = { victim, body -> messagingTemplate.convertAndSend("/sub/chat/room/${victim.roomUuid}", body) },
        ),
        Target(
            name = "AT-003-2 다른 방 이벤트 목적지",
            subscription = { victim -> "/sub/chat/room/${victim.roomUuid}/events" },
            attackDestination = { victim -> "/sub/chat/room/${victim.roomUuid}/events" },
            sendControl = { victim, body ->
                messagingTemplate.convertAndSend("/sub/chat/room/${victim.roomUuid}/events", body)
            },
        ),
        // SimpUserRegistry stays empty (see the task's logs/red-observations.md), so a name-only
        // convertAndSendToUser reaches nobody; the control goes by the victim's session id instead.
        Target(
            name = "AT-003-3 다른 유저 오류 큐",
            subscription = { _ -> USER_ERROR_QUEUE },
            attackDestination = { victim -> "/user/${victim.userUuid}${ChatController.ERROR_QUEUE}" },
            sendControl = { _, body -> sendToVictimSession(body) },
        ),
    )

    /** Session ids of `/user/queue/errors` subscriptions, in subscribe order. */
    class SubscriptionRecorder {
        val errorQueueSessionIds = LinkedBlockingQueue<String>()

        @EventListener
        fun on(event: SessionSubscribeEvent) {
            val accessor = SimpMessageHeaderAccessor.wrap(event.message)
            val sessionId = accessor.sessionId ?: return
            if (accessor.destination == USER_ERROR_QUEUE) errorQueueSessionIds.add(sessionId)
        }
    }

    @TestConfiguration
    class RecorderConfig {
        @Bean
        fun subscriptionRecorder() = SubscriptionRecorder()
    }

    init {
        extensions(SpringExtension())

        afterEach {
            clients.forEach { it.close() }
            clients.clear()
            subscriptions.errorQueueSessionIds.clear()
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
        val data = chatTestDataHelper.createTestData()
        val victim = Victim(data.userUuid, data.roomUuid)
        val attackerUuid = chatTestDataHelper.addMember(victim.roomUuid, "attacker", MemberStatus.BANNED)
        val attackerRoomUuid = chatTestDataHelper.addRoomFor(attackerUuid)

        val victimClient = connected(victim.roomUuid, victim.userUuid)
        victimClient.subscribe(target.subscription(victim))
        awaitSubscriptionReady(victimClient) { body -> target.sendControl(victim, body) }

        val attacker = connected(attackerRoomUuid, attackerUuid)
        attacker.sendTo(target.attackDestination(victim), forgedBody())

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

    // Same as @SendToUser(broadcast = false) with no registered user: the session id stands in for
    // the user name, and DefaultUserDestinationResolver routes a name equal to the session id to it.
    private fun sendToVictimSession(body: Map<String, String>) {
        val sessionId = subscriptions.errorQueueSessionIds.peek() ?: return
        val headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE)
        headers.sessionId = sessionId
        headers.setLeaveMutable(true)
        messagingTemplate.convertAndSendToUser(sessionId, ChatController.ERROR_QUEUE, body, headers.messageHeaders)
    }

    private fun forgedBody() = """{"marker":"$FORGED"}"""

    private fun tokenOf(userUuid: UUID) = jwtProvider.generateAccessToken(userUuid, Role.USER)

    companion object {
        private const val USER_ERROR_QUEUE = "/user${ChatController.ERROR_QUEUE}"
        private const val CONTROL = "control-frame"
        private const val FORGED = "forged-frame"
        private const val TIMEOUT_MILLIS = 5000L
        private const val CONTROL_RETRY_MILLIS = 300L
        private const val SILENCE_MILLIS = 1000L
    }
}
