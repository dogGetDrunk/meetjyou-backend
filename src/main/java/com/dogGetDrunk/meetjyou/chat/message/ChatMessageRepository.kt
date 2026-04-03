package com.dogGetDrunk.meetjyou.chat.message

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {

    fun findByUuid(uuid: UUID): ChatMessage?

    @Query(
        """
        select cm
        from ChatMessage cm
        join fetch cm.sender
        where cm.room.uuid = :roomUuid
        order by cm.createdAt desc, cm.id desc
        """
    )
    fun findLatestMessages(
        @Param("roomUuid") roomUuid: UUID,
        pageable: Pageable,
    ): List<ChatMessage>

    @Query(
        """
        select cm
        from ChatMessage cm
        join fetch cm.sender
        where cm.room.uuid = :roomUuid
          and (
            cm.createdAt < :cursorCreatedAt
            or (cm.createdAt = :cursorCreatedAt and cm.id < :cursorId)
          )
        order by cm.createdAt desc, cm.id desc
        """
    )
    fun findOlderMessages(
        @Param("roomUuid") roomUuid: UUID,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        pageable: Pageable,
    ): List<ChatMessage>

    @Query(
        """
        select cm
        from ChatMessage cm
        join fetch cm.room
        where cm.room.uuid in :roomUuids
          and not exists (
            select 1
            from ChatMessage newer
            where newer.room.uuid = cm.room.uuid
              and (
                newer.createdAt > cm.createdAt
                or (newer.createdAt = cm.createdAt and newer.id > cm.id)
              )
          )
        """
    )
    fun findLatestMessagesByRoomUuids(
        @Param("roomUuids") roomUuids: Collection<UUID>,
    ): List<ChatMessage>


    fun countByRoom_UuidAndSender_UuidNot(
        roomUuid: UUID,
        senderUuid: UUID,
    ): Long

    fun countByRoom_UuidAndCreatedAtAfterAndSender_UuidNot(
        roomUuid: UUID,
        createdAt: Instant,
        senderUuid: UUID,
    ): Long
}
