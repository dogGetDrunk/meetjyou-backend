package com.dogGetDrunk.meetjyou.chat.participant

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatParticipantRepository : JpaRepository<ChatParticipant, Long> {

    fun findByUser_UuidAndRoom_Uuid(
        userUuid: UUID,
        roomUuid: UUID,
    ): ChatParticipant?

    fun findAllByRoom_UuidAndUser_UuidIn(
        roomUuid: UUID,
        userUuids: Collection<UUID>,
    ): List<ChatParticipant>
}
