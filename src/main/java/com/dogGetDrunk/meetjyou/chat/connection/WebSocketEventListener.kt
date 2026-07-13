package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.chat.ChatReadService
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.UUID

@Component
class WebSocketEventListener(
    private val chatSessionTracker: ChatSessionTracker,
    private val chatParticipantService: ChatParticipantService,
    private val chatReadService: ChatReadService,
) {

    private val log = LoggerFactory.getLogger(WebSocketEventListener::class.java)

    @EventListener
    fun handleConnect(event: SessionConnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionAttributes = accessor.sessionAttributes ?: return

        val roomUuid = sessionAttributes["roomUuid"] as? UUID
        val userUuid = sessionAttributes["userUuid"] as? UUID
        val sessionId = accessor.sessionId

        if (roomUuid == null || userUuid == null || sessionId == null) {
            log.warn("WebSocket connect event ignored because session attributes are missing.")
            return
        }

        chatSessionTracker.connectUser(roomUuid, userUuid, sessionId)
        try {
            chatParticipantService.enterRoom(roomUuid, userUuid)
            chatReadService.markLatestMessageAsRead(roomUuid, userUuid)
        } catch (e: Exception) {
            chatSessionTracker.disconnectUser(roomUuid, userUuid, sessionId)
            throw e
        }

        log.info("WebSocket connection established. roomUuid={}, userUuid={}, sessionId={}", roomUuid, userUuid, sessionId)
    }

    @EventListener
    fun handleDisconnect(event: SessionDisconnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionAttributes = accessor.sessionAttributes ?: return

        val roomUuid = sessionAttributes["roomUuid"] as? UUID
        val userUuid = sessionAttributes["userUuid"] as? UUID
        val sessionId = accessor.sessionId

        if (roomUuid == null || userUuid == null || sessionId == null) {
            log.warn("WebSocket disconnect event ignored because session attributes are missing.")
            return
        }

        chatSessionTracker.disconnectUser(roomUuid, userUuid, sessionId)

        log.info("WebSocket connection closed. roomUuid={}, userUuid={}, sessionId={}", roomUuid, userUuid, sessionId)
    }
}
