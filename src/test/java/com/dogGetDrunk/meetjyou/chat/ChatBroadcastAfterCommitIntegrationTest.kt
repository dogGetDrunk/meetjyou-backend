package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.event.ChatRoomEventBroadcaster
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.support.ChatTestDataHelper
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Guards issue #151: a WebSocket broadcast must leave only after the transaction that produced it
 * commits. Sending mid-transaction lets clients see rows that are not yet visible (or that roll back
 * and never exist), so subscribers must observe nothing until commit and nothing at all on rollback.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
)
class ChatBroadcastAfterCommitIntegrationTest : BehaviorSpec() {

    @MockBean
    private lateinit var messagingTemplate: SimpMessagingTemplate

    @Autowired
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var chatRoomEventBroadcaster: ChatRoomEventBroadcaster

    @Autowired
    private lateinit var chatTestDataHelper: ChatTestDataHelper

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private fun sentDestinations(): List<String> =
        Mockito.mockingDetails(messagingTemplate).invocations
            .filter { it.method.name == "convertAndSend" }
            .map { it.arguments[0].toString() }

    private fun inTransaction(rollback: Boolean, block: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            block()
            if (rollback) {
                status.setRollbackOnly()
            }
        }
    }

    init {
        extensions(SpringExtension())

        given("채팅방 멤버의 메시지 전송") {
            lateinit var data: ChatTestDataHelper.TestData

            beforeEach {
                Mockito.clearInvocations(messagingTemplate)
                data = chatTestDataHelper.createTestData()
            }

            afterEach {
                chatTestDataHelper.cleanup()
            }

            `when`("트랜잭션이 롤백되면") {
                then("메시지도 방 이벤트도 전송되지 않는다") {
                    inTransaction(rollback = true) {
                        val request = ChatMessageRequest(roomUuid = data.roomUuid, message = "ghost")
                        chatService.handleChatMessage(request, data.userUuid)
                    }

                    sentDestinations().shouldBeEmpty()
                }
            }

            `when`("트랜잭션이 커밋되면") {
                then("커밋 전에는 아무것도 전송되지 않고, 커밋 후 메시지 → 읽음 이벤트 순으로 전송된다") {
                    var sentBeforeCommit: List<String> = emptyList()

                    inTransaction(rollback = false) {
                        val request = ChatMessageRequest(roomUuid = data.roomUuid, message = "hello")
                        chatService.handleChatMessage(request, data.userUuid)
                        sentBeforeCommit = sentDestinations()
                    }

                    sentBeforeCommit.shouldBeEmpty()
                    sentDestinations() shouldContainExactly listOf(
                        "/sub/chat/room/${data.roomUuid}",
                        "/sub/chat/room/${data.roomUuid}/events",
                    )
                }
            }
        }

        given("방 이벤트 브로드캐스트") {
            val roomUuid = UUID.randomUUID()
            val partyUuid = UUID.randomUUID()
            val userUuid = UUID.randomUUID()

            beforeEach {
                Mockito.clearInvocations(messagingTemplate)
            }

            `when`("트랜잭션이 롤백되면") {
                then("전송되지 않는다") {
                    inTransaction(rollback = true) {
                        chatRoomEventBroadcaster.broadcastMemberLeft(roomUuid, partyUuid, userUuid)
                    }

                    sentDestinations().shouldBeEmpty()
                }
            }

            `when`("트랜잭션 밖에서 호출되면") {
                then("즉시 전송된다") {
                    chatRoomEventBroadcaster.broadcastMemberLeft(roomUuid, partyUuid, userUuid)

                    sentDestinations() shouldBe listOf("/sub/chat/room/$roomUuid/events")
                }
            }
        }
    }
}
