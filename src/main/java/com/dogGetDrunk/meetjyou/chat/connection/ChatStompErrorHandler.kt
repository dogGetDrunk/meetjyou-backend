package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.exception.business.BusinessException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.util.MimeTypeUtils
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler

/**
 * Turns a rejected CONNECT / SUBSCRIBE (thrown by [ChatStompInterceptor]) into an ERROR frame with
 * the same [ErrorResponse] body the HTTP API returns, so the app can branch on `errorCode` (e.g.
 * EXPIRED_TOKEN → refresh and reconnect). Anything that isn't a domain exception gets a generic
 * body: its message may carry internals.
 */
@Component
class ChatStompErrorHandler(
    private val objectMapper: ObjectMapper,
) : StompSubProtocolErrorHandler() {

    private val log = LoggerFactory.getLogger(ChatStompErrorHandler::class.java)

    override fun handleClientMessageProcessingError(
        clientMessage: Message<ByteArray>?,
        ex: Throwable,
    ): Message<ByteArray>? {
        val business = findBusinessException(ex)
        val body = if (business != null) {
            log.info("STOMP frame rejected: {}", business.errorCode)
            ErrorResponse(business.errorCode, business.value)
        } else {
            log.error("Unexpected error while processing a STOMP frame.", ex)
            ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR)
        }

        val errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR)
        errorAccessor.message = body.errorCode
        errorAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON)
        errorAccessor.setLeaveMutable(true)

        val clientAccessor = clientMessage?.let {
            MessageHeaderAccessor.getAccessor(it, StompHeaderAccessor::class.java)
        }
        return handleInternal(errorAccessor, objectMapper.writeValueAsBytes(body), ex, clientAccessor)
    }

    // Interceptor exceptions arrive wrapped (MessageDeliveryException), so walk the cause chain.
    private fun findBusinessException(ex: Throwable): BusinessException? =
        generateSequence(ex) { it.cause }.filterIsInstance<BusinessException>().firstOrNull()
}
