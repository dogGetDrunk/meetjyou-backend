package com.dogGetDrunk.meetjyou.chat.room.dto

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import java.util.UUID

data class ChatRoomResponse(
    val uuid: UUID,
    val partyUuid: UUID,
) {
    companion object {
        fun of(chatRoom: ChatRoom) = ChatRoomResponse(
            uuid = chatRoom.uuid,
            partyUuid = chatRoom.party.uuid,
        )
    }
}
