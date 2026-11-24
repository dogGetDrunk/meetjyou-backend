package com.dogGetDrunk.meetjyou.chat.connection

import com.dogGetDrunk.meetjyou.auth.jwt.JwtProvider
import com.dogGetDrunk.meetjyou.chat.room.ChatRoomRepository
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.user.UserRepository
import com.dogGetDrunk.meetjyou.userparty.UserPartyRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.mockk
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import java.util.UUID

class ChatStompInterceptorFrameGuardTest : BehaviorSpec() {

    private val jwtProvider = mockk<JwtProvider>()
    private val chatRoomRepository = mockk<ChatRoomRepository>()
    private val userPartyRepository = mockk<UserPartyRepository>()
    private val userRepository = mockk<UserRepository>()
    private val channel = mockk<MessageChannel>()
    private val sut = ChatStompInterceptor(jwtProvider, chatRoomRepository, userPartyRepository, userRepository)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    private fun sendMessage(destination: String?): Message<ByteArray> =
        StompHeaderAccessor.create(StompCommand.SEND).run {
            destination?.let { setDestination(it) }
            setSessionAttributes(mutableMapOf())
            setLeaveMutable(true)
            MessageBuilder.createMessage(ByteArray(0), messageHeaders)
        }

    private fun connectMessage(command: StompCommand, attributes: MutableMap<String, Any>): Message<ByteArray> =
        StompHeaderAccessor.create(command).run {
            addNativeHeader("roomUuid", UUID.randomUUID().toString())
            addNativeHeader("Authorization", "Bearer token")
            setSessionAttributes(attributes)
            setLeaveMutable(true)
            MessageBuilder.createMessage(ByteArray(0), messageHeaders)
        }

    init {
        given("AT-003-4: STOMP SEND의 destination이") {
            listOf(null, "/sub/chat/room/${UUID.randomUUID()}", "/user/someone/queue/errors", "/pubx/chat", "/pub")
                .forEach { destination ->
                    `when`("$destination 이면") {
                        then("InvalidInputException으로 거절된다") {
                            shouldThrow<InvalidInputException> { sut.preSend(sendMessage(destination), channel) }
                        }
                    }
                }

            `when`("/pub/chat/message 이면") {
                then("그대로 통과한다") {
                    val message = sendMessage("/pub/chat/message")
                    shouldNotThrowAny { sut.preSend(message, channel) shouldBe message }
                }
            }
        }

        given("AT-004-2: 세션이 이미 방 A에 묶인 상태에서") {
            listOf(StompCommand.CONNECT, StompCommand.STOMP).forEach { command ->
                `when`("${command.name} 프레임이 다시 오면") {
                    then("토큰과 DB 검사 없이 거절되고 세션의 방은 A 그대로다") {
                        val boundRoomUuid = UUID.randomUUID()
                        val attributes = mutableMapOf<String, Any>(ChatSessionAttributes.ROOM_UUID to boundRoomUuid)

                        shouldThrow<InvalidInputException> {
                            sut.preSend(connectMessage(command, attributes), channel)
                        }

                        attributes[ChatSessionAttributes.ROOM_UUID] shouldBe boundRoomUuid
                        confirmVerified(jwtProvider, chatRoomRepository, userPartyRepository, userRepository)
                    }
                }
            }
        }
    }
}
