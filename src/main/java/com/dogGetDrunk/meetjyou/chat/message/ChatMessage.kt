package com.dogGetDrunk.meetjyou.chat.message

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.time.Instant
import java.util.UUID

// Test profile drives its schema off these JPA annotations (ddl-auto: create-drop, flyway
// disabled), so the unique constraint from V32 must be declared here too, not only in SQL -
// otherwise H2 never enforces it and a concurrent-duplicate test would pass without exercising
// the real constraint. See IdempotencyKey for the same pattern.
@Entity
@Table(
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_chat_message_room_sender_client_id",
            columnNames = ["room_id", "sender_id", "client_message_id"],
        )
    ],
)
class ChatMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    val uuid: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,

    @Column(length = 1000, nullable = false)
    val body: String,

    // Client-generated id for a single send attempt; lets a WS reconnect-and-resend after a lost
    // ack be recognized as the same message instead of persisted twice. Null for older clients
    // that don't send it yet, so no dedup is possible for them.
    @Column(name = "client_message_id")
    @JdbcTypeCode(Types.VARCHAR)
    val clientMessageId: UUID? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
