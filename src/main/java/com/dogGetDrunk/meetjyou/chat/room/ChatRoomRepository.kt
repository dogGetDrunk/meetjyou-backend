package com.dogGetDrunk.meetjyou.chat.room

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatRoomRepository : JpaRepository<ChatRoom, Long> {

    fun findByUuid(uuid: UUID): ChatRoom?
}
