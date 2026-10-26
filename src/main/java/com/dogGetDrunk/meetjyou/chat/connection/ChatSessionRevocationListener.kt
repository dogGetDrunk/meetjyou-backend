package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.user.UserSessionsRevokedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Drops presence and closes open chat sessions once a removal commits. Withdrawal clears presence
 * through the disconnect events the close triggers.
 *
 * After commit: a rolled-back kick must not drop the member's presence or connection. The reconnect
 * that follows is checked again on CONNECT and rejected.
 *
 * fallbackExecution: without it an event published outside a transaction is silently dropped.
 */
@Component
class ChatSessionRevocationListener(
    private val sessionRegistry: ChatWebSocketSessionRegistry,
    private val chatSessionTracker: ChatSessionTracker,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: ChatAccessRevokedEvent) {
        // Presence first: push routing reads it, and the disconnect event from the close may lag.
        chatSessionTracker.disconnectAllSessions(event.roomUuid, event.userUuid)
        sessionRegistry.closeSessions(event.userUuid, event.roomUuid, ErrorCode.CHATROOM_ACCESS_DENIED.name)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: UserSessionsRevokedEvent) {
        sessionRegistry.closeSessions(event.userUuid, roomUuid = null, reason = ErrorCode.USER_WITHDRAWN.name)
    }
}
