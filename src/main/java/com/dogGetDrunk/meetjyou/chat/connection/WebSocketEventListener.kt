package com.dogGetDrunk.meetjyou.chat.connection

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
) {

    private val log = LoggerFactory.getLogger(WebSocketEventListener::class.java)

    @EventListener
    fun handleConnect(event: SessionConnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionAttributes = accessor.sessionAttributes ?: return

        val roomUuid = sessionAttributes["roomUuid"] as? UUID
        val userUuid = sessionAttributes["userUuid"] as? UUID

        if (roomUuid == null || userUuid == null) {
            log.warn("WebSocket connect event ignored because session attributes are missing.")
            return
        }

        chatSessionTracker.connectUser(roomUuid, userUuid)
        chatParticipantService.enterRoom(roomUuid, userUuid)

        log.info("WebSocket connection established. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }

    @EventListener
    fun handleDisconnect(event: SessionDisconnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionAttributes = accessor.sessionAttributes ?: return

        val roomUuid = sessionAttributes["roomUuid"] as? UUID
        val userUuid = sessionAttributes["userUuid"] as? UUID

        if (roomUuid == null || userUuid == null) {
            log.warn("WebSocket disconnect event ignored because session attributes are missing.")
            return
        }

        chatSessionTracker.disconnectUser(roomUuid, userUuid)

        log.info("WebSocket connection closed. roomUuid={}, userUuid={}", roomUuid, userUuid)
    }
}
