package com.dogGetDrunk.meetjyou.chat.participant

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ChatParticipantRepository : JpaRepository<ChatParticipant, Long> {

    @Modifying
    @Query("delete from ChatParticipant cp where cp.room.uuid = :roomUuid")
    fun deleteAllByRoomUuid(@Param("roomUuid") roomUuid: UUID)

    fun findByUser_UuidAndRoom_Uuid(
        userUuid: UUID,
        roomUuid: UUID,
    ): ChatParticipant?

    fun findAllByRoom_UuidAndUser_UuidIn(
        roomUuid: UUID,
        userUuids: Collection<UUID>,
    ): List<ChatParticipant>

    fun deleteByUser_UuidAndRoom_Uuid(
        userUuid: UUID,
        roomUuid: UUID,
    )
}
