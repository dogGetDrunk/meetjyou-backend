package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.support.ChatTestDataHelper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class ChatIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var chatTestDataHelper: ChatTestDataHelper

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

    init {
        extensions(SpringExtension())

        given("valid JWT and active party membership") {
            lateinit var token: String
            lateinit var roomUuid: UUID

            beforeEach {
                val data = chatTestDataHelper.createTestData()
                token = jwtProvider.generateAccessToken(data.userUuid, data.userEmail)
                roomUuid = data.roomUuid
            }

            afterEach {
                chatTestDataHelper.cleanup()
            }

            `when`("user connects via STOMP and sends a message") {
                then("broadcast is received on the chat room subscription") {
                    val received = CompletableFuture<String>()

                    val client = WebSocketStompClient(StandardWebSocketClient()).apply {
                        messageConverter = MappingJackson2MessageConverter()
                    }

                    val connectHeaders = StompHeaders().apply {
                        add("roomUuid", roomUuid.toString())
                        add("Authorization", "Bearer $token")
                    }

                    val connectError = CompletableFuture<Throwable>()
                    val session = client.connectAsync(
                        "ws://localhost:$port/ws-chat",
                        null,
                        connectHeaders,
                        object : StompSessionHandlerAdapter() {
                            override fun handleException(
                                s: org.springframework.messaging.simp.stomp.StompSession,
                                command: org.springframework.messaging.simp.stomp.StompCommand?,
                                headers: StompHeaders,
                                payload: ByteArray,
                                exception: Throwable,
                            ) { connectError.complete(exception) }

                            override fun handleTransportError(
                                s: org.springframework.messaging.simp.stomp.StompSession,
                                exception: Throwable,
                            ) { connectError.complete(exception) }
                        },
                    ).get(5, TimeUnit.SECONDS)

                    session shouldNotBe null

                    session.subscribe(
                        "/sub/chat/room/$roomUuid",
                        object : StompFrameHandler {
                            override fun getPayloadType(headers: StompHeaders) = Map::class.java
                            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                                (payload as? Map<*, *>)?.get("body")
                                    ?.let { received.complete(it.toString()) }
                            }
                        },
                    )

                    delay(300)

                    if (connectError.isDone) throw connectError.get()

                    session.send(
                        "/pub/chat/message",
                        ChatMessageRequest(roomUuid = roomUuid, message = "hello from test"),
                    )

                    withTimeout(5000L) { received.await() } shouldBe "hello from test"
                    session.disconnect()
                }
            }
        }
    }
}
