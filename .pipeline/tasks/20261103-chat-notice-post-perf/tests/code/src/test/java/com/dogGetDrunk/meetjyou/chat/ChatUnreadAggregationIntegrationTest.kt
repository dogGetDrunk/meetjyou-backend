package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.message.ChatMessage
import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.common.util.CurrentUserProvider
import com.dogGetDrunk.meetjyou.party.Party
import com.dogGetDrunk.meetjyou.user.AuthProvider
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.userparty.PartyRole
import com.dogGetDrunk.meetjyou.userparty.UserParty
import com.google.firebase.FirebaseApp
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.workrequests.WorkRequestClient
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Collections

class UnreadSqlInspector : StatementInspector {
    override fun inspect(sql: String): String {
        statements.add(sql)
        return sql
    }

    companion object {
        private val statements = Collections.synchronizedList(mutableListOf<String>())

        fun reset() = statements.clear()

        fun unreadSelectCount(): Int = synchronized(statements) {
            statements.count {
                val sql = it.trim().lowercase()
                sql.startsWith("select") && sql.contains("chat_message") && sql.contains("count(")
            }
        }
    }
}

private const val STATEMENT_INSPECTOR_KEY = "spring.jpa.properties.hibernate.session_factory.statement_inspector"

@SpringBootTest(properties = [
    "$STATEMENT_INSPECTOR_KEY=com.dogGetDrunk.meetjyou.chat.UnreadSqlInspector",
    "spring.jpa.properties.hibernate.cache.use_query_cache=false",
    "spring.jpa.properties.hibernate.cache.use_second_level_cache=false",
])
@ActiveProfiles("test")
@MockBean(
    FirebaseApp::class,
    AuthenticationDetailsProvider::class,
    ObjectStorageClient::class,
    WorkRequestClient::class,
    SimpMessagingTemplate::class,
)
class ChatUnreadAggregationIntegrationTest : BehaviorSpec() {
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var chatReadService: ChatReadService
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @MockBean private lateinit var currentUserProvider: CurrentUserProvider

    init {
        extensions(SpringExtension())

        given("AT-001-2 diverse cursors, memberships and messages") {
            `when`("the requester loads joined rooms") {
                then("counts and room selection are isolated from other users") {
                    inFixture {
                        val a = user("reader")
                        val b = user("sender")
                        val r1 = room(a)
                        repeat(3) { message(r1.first, b) }
                        repeat(2) { message(r1.first, a) }
                        val r2 = room(a)
                        val k = message(r2.first, b)
                        r2.second.updateLastReadMessageId(k.id)
                        repeat(2) { message(r2.first, b) }
                        message(r2.first, a)
                        val otherMembership = membership(r2.first, b)
                        otherMembership.updateLastReadMessageId(k.id + 1)
                        val r3 = room(a)
                        val last = message(r3.first, b)
                        r3.second.updateLastReadMessageId(last.id)
                        val r4 = room(a)
                        val outside = room(b)
                        message(outside.first, b)
                        message(outside.first, a)
                        val inactive = room(a)
                        inactive.second.pending()
                        message(inactive.first, b)
                        prepare(a)

                        val actual = chatReadService.getChatRooms().rooms.associate { it.roomUuid to it.unreadCount }
                        actual shouldBe mapOf(
                            r1.first.uuid to 3L,
                            r2.first.uuid to 2L,
                            r3.first.uuid to 0L,
                            r4.first.uuid to 0L,
                        )
                    }
                }
            }
        }

        listOf(0, 1, 10).forEach { roomCount ->
            given("AT-001-2 $roomCount fresh joined rooms") {
                `when`("the room list is loaded after flush and clear") {
                    then("the unread aggregation executes ${if (roomCount == 0) 0 else 1} select statements") {
                        inFixture {
                            val a = user("reader")
                            val b = user("sender")
                            val rooms = (0 until roomCount).map { index ->
                                val (room, member) = room(a)
                                val first = message(room, b)
                                if (index % 2 == 0) member.updateLastReadMessageId(first.id)
                                message(room, b)
                                room.uuid to if (index % 2 == 0) 1L else 2L
                            }.toMap()
                            prepare(a)

                            val actual = chatReadService.getChatRooms().rooms
                                .associate { it.roomUuid to it.unreadCount }
                            actual shouldBe rooms
                            UnreadSqlInspector.unreadSelectCount() shouldBe if (roomCount == 0) 0 else 1
                        }
                    }
                }
            }
        }
    }

    private fun inFixture(block: () -> Unit) {
        TransactionTemplate(transactionManager).executeWithoutResult { status ->
            try {
                block()
            } finally {
                status.setRollbackOnly()
            }
        }
    }

    private fun user(name: String): User {
        val user = User(
            email = "$name@example.com",
            nickname = name,
            authProvider = AuthProvider.KAKAO,
            externalId = name,
        )
        entityManager.persist(user)
        return user
    }

    private fun room(user: User): Pair<ChatRoom, UserParty> {
        val now = Instant.now()
        val party = Party(now, now.plusSeconds(86400), "Seoul", 1, 4, "Test Party")
        entityManager.persist(party)
        val room = ChatRoom(party = party)
        entityManager.persist(room)
        return room to membership(room, user)
    }

    private fun membership(room: ChatRoom, user: User): UserParty {
        val membership = UserParty(party = room.party, user = user, role = PartyRole.MEMBER)
        entityManager.persist(membership)
        return membership
    }

    private fun message(room: ChatRoom, sender: User): ChatMessage {
        val message = ChatMessage(room = room, sender = sender, body = "message")
        entityManager.persist(message)
        return message
    }

    private fun prepare(requester: User) {
        val uuid = requester.uuid
        entityManager.flush()
        entityManager.clear()
        Mockito.`when`(currentUserProvider.uuid).thenReturn(uuid)
        UnreadSqlInspector.reset()
    }
}
