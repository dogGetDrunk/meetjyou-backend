package com.dogGetDrunk.meetjyou.chat.participant

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.user.User
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "chat_participant",
    uniqueConstraints = [
        UniqueConstraint(name = ChatParticipant.UNIQUE_USER_ROOM, columnNames = ["user_id", "room_id"])
    ]
)
class ChatParticipant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: ChatRoom,
) {
    companion object {
        const val UNIQUE_USER_ROOM = "uk_chat_participant_user_room"
    }
}
