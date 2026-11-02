package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.chat.ChatReadService
import com.dogGetDrunk.meetjyou.chat.participant.ChatParticipantService
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.sql.SQLException
import java.util.UUID
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.socket.messaging.SessionConnectEvent

class WebSocketEventListenerTest : BehaviorSpec() {

    private val chatSessionTracker = mockk<ChatSessionTracker>(relaxed = true)
    private val chatParticipantService = mockk<ChatParticipantService>()
    private val chatReadService = mockk<ChatReadService>(relaxed = true)
    private val sut = WebSocketEventListener(chatSessionTracker, chatParticipantService, chatReadService)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private val roomUuid = UUID.randomUUID()
    private val userUuid = UUID.randomUUID()

    private fun connectEvent(): SessionConnectEvent {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        accessor.sessionId = SESSION_ID
        accessor.sessionAttributes = mutableMapOf<String, Any>(
            ChatSessionAttributes.ROOM_UUID to roomUuid,
            ChatSessionAttributes.USER_UUID to userUuid,
        )
        return SessionConnectEvent(this, MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders))
    }

    private fun violation(constraintName: String) = DataIntegrityViolationException(
        "could not execute statement",
        ConstraintViolationException("Duplicate entry", SQLException("Duplicate entry"), constraintName),
    )

    init {
        given("같은 유저의 동시 connect로 참여자 insert 경합에서 졌을 때") {
            `when`("chat_participant 유니크 제약 위반이면") {
                then("AT-3-2 이미 입장한 것으로 보고 세션을 유지한 채 읽음 처리를 진행한다") {
                    every { chatParticipantService.enterRoom(roomUuid, userUuid) } throws
                        violation("chat_participant.uk_chat_participant_user_room")

                    shouldNotThrowAny { sut.handleConnect(connectEvent()) }

                    verify(exactly = 0) { chatSessionTracker.disconnectUser(any(), any(), any()) }
                    verify { chatReadService.markLatestMessageAsRead(roomUuid, userUuid) }
                }
            }

            `when`("다른 제약 위반이면") {
                then("AT-3-2 기존처럼 세션 추적을 되돌리고 예외를 전파한다") {
                    every { chatParticipantService.enterRoom(roomUuid, userUuid) } throws
                        violation("chat_participant.other")

                    shouldThrow<DataIntegrityViolationException> { sut.handleConnect(connectEvent()) }

                    verify { chatSessionTracker.disconnectUser(roomUuid, userUuid, SESSION_ID) }
                    verify(exactly = 0) { chatReadService.markLatestMessageAsRead(any(), any()) }
                }
            }
        }
    }

    companion object {
        private const val SESSION_ID = "session-1"
    }
}
