package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.connection.ChatAccessRevocationReason
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import com.dogGetDrunk.meetjyou.chat.support.ChatTestDataHelper
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.user.Role
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.user.UserSessionRevoker
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * #221: a connection must not outlive the access it was granted for. Covers the subscription
 * allowlist and the server closing sessions on kick / leave / withdrawal.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class ChatAccessRevocationIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var chatTestDataHelper: ChatTestDataHelper

    @Autowired
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @Autowired
    private lateinit var chatParticipantService: ChatParticipantService

    @Autowired
    private lateinit var userSessionRevoker: UserSessionRevoker

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Value("\${local.server.port}")
    private var port: Int = 0

    init {
        extensions(SpringExtension())

        given("방에 연결된 활성 멤버") {
            lateinit var userUuid: UUID
            lateinit var roomUuid: UUID
            lateinit var token: String
            lateinit var client: TestClient

            beforeEach {
                val data = chatTestDataHelper.createTestData()
                userUuid = data.userUuid
                roomUuid = data.roomUuid
                token = jwtProvider.generateAccessToken(userUuid, Role.USER)
                client = connect(roomUuid, token)
            }

            afterEach {
                if (client.session.isConnected) client.session.disconnect()
                chatTestDataHelper.cleanup()
            }

            `when`("와일드카드 destination(/sub/**)을 구독하면") {
                then("ERROR 프레임으로 거절되고 다른 방 메시지를 받지 못한다") {
                    val leaked = client.subscribe("/sub/**")
                    delay(SETTLE_MILLIS)
                    messagingTemplate.convertAndSend("/sub/chat/room/${UUID.randomUUID()}", mapOf("body" to "secret"))

                    val errorCode = withTimeout(TIMEOUT_MILLIS) { client.errorCode.await() }
                    errorCode shouldBe ErrorCode.INVALID_INPUT_VALUE.name
                    delay(SETTLE_MILLIS)
                    leaked.isDone shouldBe false
                }
            }

            `when`("멤버인 다른 방을 이 연결로 구독하면") {
                then("연결된 방이 아니므로 CHATROOM_ACCESS_DENIED로 거절된다") {
                    val otherRoomUuid = chatTestDataHelper.addRoomFor(userUuid)
                    client.subscribe("/sub/chat/room/$otherRoomUuid")

                    val errorCode = withTimeout(TIMEOUT_MILLIS) { client.errorCode.await() }
                    errorCode shouldBe ErrorCode.CHATROOM_ACCESS_DENIED.name
                }
            }

            `when`("연결된 방의 메시지·이벤트 destination을 구독하면") {
                then("둘 다 허용되고 연결이 유지된다") {
                    client.subscribe("/sub/chat/room/$roomUuid")
                    client.subscribe("/sub/chat/room/$roomUuid/events")
                    delay(SETTLE_MILLIS)

                    client.errorCode.isDone shouldBe false
                    client.session.isConnected shouldBe true
                }
            }

            `when`("멤버가 방에서 제거되면(강퇴·나가기)") {
                then("커밋 후 서버가 열린 세션을 닫는다") {
                    chatParticipantService.removeParticipant(
                        roomUuid,
                        userUuid,
                        ChatAccessRevocationReason.MEMBER_BANNED,
                    )

                    withTimeout(TIMEOUT_MILLIS) { client.closed.await() }
                    client.session.isConnected shouldBe false
                }
            }

            ChatAccessRevocationReason.entries.forEach { reason ->
                `when`("${reason.name} 사유로 제거되면") {
                    then("close code 1008, reason = ${reason.name}으로 닫혀 앱이 강퇴와 나가기를 구분한다") {
                        val raw = connectRaw(roomUuid, token)

                        chatParticipantService.removeParticipant(roomUuid, userUuid, reason)

                        val status = withTimeout(TIMEOUT_MILLIS) { raw.closeStatus.await() }
                        status.code shouldBe CloseStatus.POLICY_VIOLATION.code
                        status.reason shouldBe reason.name
                    }
                }
            }

            `when`("제거 트랜잭션이 롤백되면") {
                then("세션은 닫히지 않는다") {
                    transactionTemplate.executeWithoutResult { status ->
                        chatParticipantService.removeParticipant(
                            roomUuid,
                            userUuid,
                            ChatAccessRevocationReason.MEMBER_BANNED,
                        )
                        status.setRollbackOnly()
                    }
                    delay(SETTLE_MILLIS)

                    client.closed.isDone shouldBe false
                    client.session.isConnected shouldBe true
                }
            }

            `when`("유저가 탈퇴해 모든 기기 세션이 회수되면") {
                then("커밋 후 그 유저의 채팅 세션도 닫힌다") {
                    transactionTemplate.executeWithoutResult {
                        val user = userRepository.findByUuid(userUuid) ?: error("user missing")
                        userSessionRevoker.revokeAll(user)
                    }

                    withTimeout(TIMEOUT_MILLIS) { client.closed.await() }
                    client.session.isConnected shouldBe false
                }
            }
        }
    }

    private class TestClient(
        val session: StompSession,
        val errorCode: CompletableFuture<String?>,
        val closed: CompletableFuture<Throwable>,
    ) {
        fun subscribe(destination: String): CompletableFuture<Any?> {
            val received = CompletableFuture<Any?>()
            session.subscribe(
                destination,
                object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders) = Map::class.java
                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        received.complete(payload)
                    }
                },
            )
            return received
        }
    }

    private class RawClient(val closeStatus: CompletableFuture<CloseStatus>)

    // WebSocketStompClient hides the close frame behind ConnectionLostException, so this speaks
    // STOMP over a plain WebSocket to read the code and reason the app would see.
    private fun connectRaw(roomUuid: UUID, token: String): RawClient {
        val connected = CompletableFuture<Unit>()
        val closeStatus = CompletableFuture<CloseStatus>()
        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                if (message.payload.startsWith("CONNECTED")) connected.complete(Unit)
            }

            override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
                closeStatus.complete(status)
            }
        }
        val session = StandardWebSocketClient().execute(handler, "ws://localhost:$port/ws-chat")
            .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val frame = "CONNECT\naccept-version:1.2\nroomUuid:$roomUuid\nAuthorization:Bearer $token\n\n\u0000"
        session.sendMessage(TextMessage(frame))
        connected.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return RawClient(closeStatus)
    }

    private fun connect(roomUuid: UUID, token: String): TestClient {
        val errorCode = CompletableFuture<String?>()
        val closed = CompletableFuture<Throwable>()
        val headers = StompHeaders().apply {
            add("roomUuid", roomUuid.toString())
            add("Authorization", "Bearer $token")
        }
        val handler = object : StompSessionHandlerAdapter() {
            override fun getPayloadType(headers: StompHeaders) = Map::class.java

            // ERROR frames land here: the client has no subscription for them.
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                errorCode.complete(headers.getFirst("message"))
            }

            override fun handleException(
                session: StompSession,
                command: StompCommand?,
                headers: StompHeaders,
                payload: ByteArray,
                exception: Throwable,
            ) = Unit

            override fun handleTransportError(session: StompSession, exception: Throwable) {
                closed.complete(exception)
            }
        }
        val client = WebSocketStompClient(StandardWebSocketClient()).apply {
            messageConverter = MappingJackson2MessageConverter()
        }
        val session = client.connectAsync("ws://localhost:$port/ws-chat", null, headers, handler)
            .get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return TestClient(session, errorCode, closed)
    }

    companion object {
        private const val SETTLE_MILLIS = 500L
        private const val TIMEOUT_MILLIS = 5000L
        private const val CONNECT_TIMEOUT_SECONDS = 5L
    }
}
