package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRepository
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.party.PartyRepository
import com.dogGetDrunk.meetjyou.support.SharedMySql
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Guards #293 on real MySQL: the chat push outbox row must fit V1's `dedup_key VARCHAR(100)`. With
 * the old 115-char key, strict mode made the AFTER_COMMIT insert fail, so the message was saved but
 * no offline member ever got a push — and H2 tests built a 255-char column and passed.
 */
// Bean overriding: component scan also picks up other dockerTest specs' nested slice configs.
@SpringBootTest(
    properties = [
        "spring.config.additional-location=file:./src/test/resources/",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
    SimpMessagingTemplate::class,
)
class ChatPushOutboxDockerTest : BehaviorSpec() {
    @Autowired private lateinit var chatService: ChatService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var parties: PartyRepository
    @Autowired private lateinit var rooms: ChatRoomRepository
    @Autowired private lateinit var memberships: UserPartyRepository
    @Autowired private lateinit var messages: ChatMessageRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }

    private data class Fixture(val roomUuid: UUID, val senderUuid: UUID, val receiverUuids: List<UUID>)

    // Nicknames stay within MySQL's VARCHAR(10).
    private fun seed(): Fixture = tx.execute {
        val party = parties.save(
            Party(
                itinStart = Instant.now(), itinFinish = Instant.now().plusSeconds(TRIP_SECONDS),
                destination = "Seoul", joined = MEMBER_COUNT, capacity = CAPACITY, name = "Chat Party",
            )
        )
        val room = rooms.save(ChatRoom(party = party))
        val members = List(MEMBER_COUNT) {
            val tag = UUID.randomUUID().toString().take(TAG_LENGTH)
            val user = users.save(User("$tag@example.com", "m$tag", AuthProvider.KAKAO, "ext-$tag"))
            memberships.save(UserParty(party = party, user = user, role = PartyRole.MEMBER))
            user.uuid
        }
        Fixture(room.uuid, members.first(), members.drop(1))
    } ?: error("seed failed")

    // Each message commits for real, so the AFTER_COMMIT handler writes its outbox rows.
    private fun send(fixture: Fixture, body: String) = tx.executeWithoutResult {
        chatService.handleChatMessage(ChatMessageRequest(roomUuid = fixture.roomUuid, message = body), fixture.senderUuid)
    }

    init {
        extensions(SpringExtension())

        given("AT-001-2: 오프라인 멤버 2명이 있는 채팅방 (MySQL 8.0.41)") {
            `when`("메시지 2개를 각각 커밋하면") {
                then("메시지와 수신자 조합마다 outbox 행이 저장된다") {
                    val fixture = seed()
                    listOf("first", "second").forEach { body -> send(fixture, body) }

                    val messageUuids = tx.execute { messages.findAll().map { it.uuid } } ?: error("read failed")
                    val expected = messageUuids.flatMap { message ->
                        fixture.receiverUuids.map { receiver -> "chat:$message:$receiver" }
                    }
                    val keys = jdbc.queryForList(OUTBOX_KEYS_SQL, String::class.java)

                    keys.size shouldBe expected.size
                    keys.toSet() shouldBe expected.toSet()
                }
            }
        }
    }

    companion object {
        private val mysql = SharedMySql.newDatabase()
        private const val MEMBER_COUNT = 3
        private const val CAPACITY = 4
        private const val TRIP_SECONDS = 86_400L
        private const val TAG_LENGTH = 8
        private const val OUTBOX_KEYS_SQL = "SELECT dedup_key FROM notification_outbox WHERE type = 'CHAT_MESSAGE'"

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
