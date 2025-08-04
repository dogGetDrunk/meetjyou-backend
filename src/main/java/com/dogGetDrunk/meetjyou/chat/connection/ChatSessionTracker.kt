package com.dogGetDrunk.meetjyou.chat.connection

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class ChatSessionTracker {

    private val connectedUsers: MutableMap<UUID, MutableSet<UUID>> = ConcurrentHashMap()

    fun connectUser(roomUuid: UUID, userUuid: UUID) {
        connectedUsers.computeIfAbsent(roomUuid) { mutableSetOf() }.add(userUuid)
    }

    fun disconnectUser(roomUuid: UUID, userUuid: UUID) {
        connectedUsers[roomUuid]?.remove(userUuid)
        if (connectedUsers[roomUuid]?.isEmpty() == true) {
            connectedUsers.remove(roomUuid)
        }
    }

    fun isUserConnected(roomUuid: UUID, userUuid: UUID): Boolean {
        return connectedUsers[roomUuid]?.contains(userUuid) == true
    }

    fun getConnectedUsers(roomUuid: UUID): Set<UUID> {
        return connectedUsers[roomUuid] ?: emptySet()
    }
}
