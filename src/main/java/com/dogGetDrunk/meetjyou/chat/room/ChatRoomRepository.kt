package com.dogGetDrunk.meetjyou.chat.room

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {
    fun findByUuid(uuid: UUID): ChatRoom?

    @Query(
        """
        select p.uuid
        from ChatRoom cr
        join cr.party p
        where cr.uuid = :roomUuid
        """
    )
    fun findPartyUuidByRoomUuid(roomUuid: UUID): UUID?

    @Query(
        """
        select cr
        from ChatRoom cr
        join fetch cr.party
        where cr.party.uuid in :partyUuids
        """
    )
    fun findAllWithPartyByPartyUuidIn(
        @Param("partyUuids") partyUuids: Collection<UUID>,
    ): List<ChatRoom>
}
