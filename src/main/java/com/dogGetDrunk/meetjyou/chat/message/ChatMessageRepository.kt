package com.dogGetDrunk.meetjyou.chat.message

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {

    fun countByRoom_UuidAndSender_UuidNot(
        roomUuid: UUID,
        senderUuid: UUID,
    ): Long

    fun countByRoom_UuidAndCreatedAtAfterAndSender_UuidNot(
        roomUuid: UUID,
        createdAt: LocalDateTime,
        senderUuid: UUID,
    ): Long
}
