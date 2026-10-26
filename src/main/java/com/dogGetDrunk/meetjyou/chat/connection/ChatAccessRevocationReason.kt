package com.dogGetDrunk.meetjyou.chat.connection

/**
 * Sent as the WebSocket close reason when the server drops a member's session. The removed member
 * never gets the room's MEMBER_BANNED / MEMBER_LEFT event (its session closes first), so this is how
 * the app tells a kick from a leave. Names match `ChatRoomEventType` on purpose.
 */
enum class ChatAccessRevocationReason {
    MEMBER_BANNED,
    MEMBER_LEFT,
}
