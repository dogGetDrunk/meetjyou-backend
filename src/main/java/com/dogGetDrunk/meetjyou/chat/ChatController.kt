package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.chat.connection.ChatSessionAttributes
import com.dogGetDrunk.meetjyou.chat.message.ChatMessage
import com.dogGetDrunk.meetjyou.chat.message.ChatMessageRequest
import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException
import com.dogGetDrunk.meetjyou.common.exception.business.auth.UnauthenticatedException
import com.dogGetDrunk.meetjyou.common.exception.violatedConstraintName
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class ChatController(
    private val chatService: ChatService,
) {

    private val log = LoggerFactory.getLogger(ChatController::class.java)

    @MessageMapping("/chat/message")
    fun handleMessage(
        request: ChatMessageRequest,
        headerAccessor: SimpMessageHeaderAccessor,
    ) {
        val senderUuid = headerAccessor.sessionAttributes?.get(ChatSessionAttributes.USER_UUID) as? UUID
            ?: throw UnauthenticatedException(SESSION_WITHOUT_USER)

        chatService.handleChatMessage(request, senderUuid)
    }

    /**
     * A failed SEND keeps the connection open, so no ERROR frame is sent; without this the sender
     * never learns the message was dropped. Replies only to the sending session.
     */
    @MessageExceptionHandler(BusinessException::class)
    @SendToUser(ERROR_QUEUE, broadcast = false)
    fun handleBusinessException(e: BusinessException): ErrorResponse {
        log.info("Chat message rejected: {}", e.errorCode)
        return ErrorResponse(e.errorCode, e.value)
    }

    /**
     * A resend racing its own original past the clientMessageId check: the original already
     * persisted and broadcast, so the resend is dropped like the pre-check would have. A null
     * return sends no frame.
     */
    @MessageExceptionHandler(DataIntegrityViolationException::class)
    @SendToUser(ERROR_QUEUE, broadcast = false)
    fun handleDataIntegrityViolation(e: DataIntegrityViolationException): ErrorResponse? {
        if (e.violatedConstraintName() != ChatMessage.UNIQUE_CLIENT_MESSAGE_ID) return handleUnexpectedException(e)
        log.info("Concurrent duplicate chat message submission ignored.")
        return null
    }

    @MessageExceptionHandler(Exception::class)
    @SendToUser(ERROR_QUEUE, broadcast = false)
    fun handleUnexpectedException(e: Exception): ErrorResponse {
        log.error("Unexpected error while handling a chat message.", e)
        return ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR)
    }

    companion object {
        const val ERROR_QUEUE = "/queue/errors"
        private const val SESSION_WITHOUT_USER = "stomp-session"
    }
}
