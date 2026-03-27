package com.dogGetDrunk.meetjyou.chat.connection

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class ChatSessionTracker {

    private val log = LoggerFactory.getLogger(ChatSessionTracker::class.java)

    private val connectedUsers: MutableMap<UUID, MutableSet<UUID>> = ConcurrentHashMap()

    fun connectUser(roomUuid: UUID, userUuid: UUID) {
        connectedUsers.computeIfAbsent(roomUuid) { ConcurrentHashMap.newKeySet() }
            .add(userUuid)

        log.debug("User connected to chat room. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    fun disconnectUser(roomUuid: UUID, userUuid: UUID) {
        connectedUsers[roomUuid]?.remove(userUuid)

        if (connectedUsers[roomUuid]?.isEmpty() == true) {
            connectedUsers.remove(roomUuid)
        }

        log.debug("User disconnected from chat room. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    fun isUserConnected(roomUuid: UUID, userUuid: UUID): Boolean {
        return connectedUsers[roomUuid]?.contains(userUuid) == true
    }

    fun getConnectedUsers(roomUuid: UUID): Set<UUID> {
        return connectedUsers[roomUuid]?.toSet() ?: emptySet()
    }
}
