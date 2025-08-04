package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.user.UserRepository
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionConnectEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.util.UUID

@Component
class WebSocketEventListener(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
    private val chatSessionTracker: ChatSessionTracker
) {

    @EventListener
    fun handleConnect(event: SessionConnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val token = accessor.getFirstNativeHeader("Authorization")?.removePrefix("Bearer ") ?: return
        val userUuid = jwtProvider.getUserUuid(token)
        val roomUuid = accessor.getFirstNativeHeader("roomUuid")?.let { UUID.fromString(it) } ?: return

        chatSessionTracker.connectUser(roomUuid, userUuid)
    }

    @EventListener
    fun handleDisconnect(event: SessionDisconnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionAttributes = accessor.sessionAttributes ?: return
        val roomUuid = sessionAttributes["roomUuid"] as? UUID ?: return
        val userUuid = sessionAttributes["userUuid"] as? UUID ?: return

        chatSessionTracker.disconnectUser(roomUuid, userUuid)
    }
}
