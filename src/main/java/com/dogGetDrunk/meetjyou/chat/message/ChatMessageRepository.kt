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
        join fetch cm.room
        where cm.uuid = :uuid
        """
    )
    fun findWithSenderAndRoomByUuid(
        @Param("uuid") uuid: UUID,
    ): ChatMessage?

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

    fun countByRoom_UuidAndIdGreaterThanAndSender_UuidNot(
        roomUuid: UUID,
        id: Long,
        senderUuid: UUID,
    ): Long

    @Query(
        """
        select cm.room.uuid as roomUuid, count(cm) as unreadCount
        from ChatMessage cm
        where cm.room.uuid in :roomUuids
          and cm.sender.uuid <> :requesterUuid
        group by cm.room.uuid
        """
    )
    fun countUnreadByRoomUuidsWithoutLastReadMessageId(
        @Param("roomUuids") roomUuids: Collection<UUID>,
        @Param("requesterUuid") requesterUuid: UUID,
    ): List<RoomUnreadCountProjection>

    @Query(
        """
        select cm.room.uuid as roomUuid, count(cm) as unreadCount
        from ChatMessage cm
        where cm.room.uuid = :roomUuid
          and cm.sender.uuid <> :requesterUuid
          and cm.id > :lastReadMessageId
        group by cm.room.uuid
        """
    )
    fun countUnreadByRoomUuidAfterLastReadMessageId(
        @Param("roomUuid") roomUuid: UUID,
        @Param("requesterUuid") requesterUuid: UUID,
        @Param("lastReadMessageId") lastReadMessageId: Long,
    ): RoomUnreadCountProjection?

    @Query(
        """
        select cm.id as messageId, count(up) as unreadCount
        from ChatMessage cm
        join cm.room cr
        join UserParty up on up.party = cr.party
        where cm.id in :messageIds
          and up.memberStatus = com.dogGetDrunk.meetjyou.userparty.MemberStatus.JOINED
          and up.user <> cm.sender
          and (up.lastReadMessageId is null or up.lastReadMessageId < cm.id)
        group by cm.id
        """
    )
    fun countUnreadByMessageIds(
        @Param("messageIds") messageIds: Collection<Long>,
    ): List<MessageUnreadCountProjection>

    @Query(
        """
        select count(up)
        from UserParty up
        where up.party.uuid = :partyUuid
          and up.memberStatus = com.dogGetDrunk.meetjyou.userparty.MemberStatus.JOINED
          and up.user.uuid <> :senderUuid
          and (up.lastReadMessageId is null or up.lastReadMessageId < :messageId)
        """
    )
    fun countUnreadByPartyUuidAndMessageId(
        @Param("partyUuid") partyUuid: UUID,
        @Param("messageId") messageId: Long,
        @Param("senderUuid") senderUuid: UUID,
    ): Long

    @Query(
        """
        select cm
        from ChatMessage cm
        join fetch cm.sender
        join fetch cm.room
        where cm.room.uuid = :roomUuid
        order by cm.id desc
        """
    )
    fun findLatestMessagesWithSenderAndRoom(
        @Param("roomUuid") roomUuid: UUID,
        pageable: Pageable,
    ): List<ChatMessage>
}

interface RoomUnreadCountProjection {
    fun getRoomUuid(): UUID
    fun getUnreadCount(): Long
}

interface MessageUnreadCountProjection {
    fun getMessageId(): Long
    fun getUnreadCount(): Long
}
