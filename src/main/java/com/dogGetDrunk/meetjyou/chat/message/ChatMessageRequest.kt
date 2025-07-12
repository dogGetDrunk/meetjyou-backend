package com.dogGetDrunk.meetjyou.chat.message

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

// 클라이언트가 /pub/chat/message 요청을 보낼 때 사용하는 DTO
data class ChatMessageRequest(
    @field:JsonProperty("room_uuid") val roomUuidString: String,
    val message: String,
) {
    val roomUuid: UUID
        get() = UUID.fromString(roomUuidString)
}
