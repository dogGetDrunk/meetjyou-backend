package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.support.ChatTestDataHelper
import com.dogGetDrunk.meetjyou.userparty.MemberStatus
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Guards issue #220: a chat message push (which carries the message body) must reach only the
 * party's current JOINED members, never applicants, rejected applicants, or members who were
 * banned or left. Runs the real recipient query and the AFTER_COMMIT outbox handler end to end.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
    SimpMessagingTemplate::class,
)
class ChatPushRecipientIntegrationTest : BehaviorSpec() {

    @Autowired
    private lateinit var chatService: ChatService

    @Autowired
    private lateinit var chatTestDataHelper: ChatTestDataHelper

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    init {
        extensions(SpringExtension())

        given("JOINED 멤버와 비활성 멤버가 섞인 파티의 채팅방") {
            afterEach {
                chatTestDataHelper.cleanup()
            }

            `when`("멤버가 메시지를 보내면") {
                then("푸시 outbox는 보낸 사람을 뺀 JOINED 멤버에게만 쌓인다") {
                    val data = chatTestDataHelper.createTestData()
                    val joined = chatTestDataHelper.addMember(data.roomUuid, "joinedMember", MemberStatus.JOINED)
                    INACTIVE_STATUSES.forEach { status ->
                        chatTestDataHelper.addMember(data.roomUuid, "member${status.name}", status)
                    }

                    TransactionTemplate(transactionManager).executeWithoutResult {
                        val request = ChatMessageRequest(roomUuid = data.roomUuid, message = "secret plan")
                        chatService.handleChatMessage(request, data.userUuid)
                    }

                    chatTestDataHelper.outboxRecipientUuids() shouldBe setOf(joined)
                }
            }
        }
    }

    companion object {
        private val INACTIVE_STATUSES = listOf(
            MemberStatus.PENDING,
            MemberStatus.REJECTED,
            MemberStatus.BANNED,
            MemberStatus.LEFT,
        )
    }
}
