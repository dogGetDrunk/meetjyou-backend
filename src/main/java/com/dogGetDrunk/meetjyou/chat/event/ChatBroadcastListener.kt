package com.dogGetDrunk.meetjyou.chat.event

import org.slf4j.LoggerFactory
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * The only place that sends chat WebSocket frames. Sending after commit guarantees a subscriber
 * never receives a row it cannot yet read back, or one that rolls back and never exists (#151).
 *
 * fallbackExecution: without it an event published outside a transaction is silently dropped.
 */
@Component
class ChatBroadcastListener(
    private val messagingTemplate: SimpMessagingTemplate,
) {

    private val log = LoggerFactory.getLogger(ChatBroadcastListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: ChatMessageBroadcastEvent) {
        send("/sub/chat/room/${event.roomUuid}", event.message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: ChatRoomEvent) {
        send("/sub/chat/room/${event.roomUuid}/events", event)
    }

    // The data is already committed here, so a send failure must not surface as a failed request:
    // the client would retry an operation that succeeded. A missed frame is recovered by refetch.
    private fun send(destination: String, payload: Any) {
        try {
            messagingTemplate.convertAndSend(destination, payload)
        } catch (e: MessagingException) {
            log.warn("Chat broadcast failed after commit. destination={}", destination, e)
        }
    }
}
