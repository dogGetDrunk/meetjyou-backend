package com.dogGetDrunk.meetjyou.chat.event

import com.dogGetDrunk.meetjyou.chat.message.ChatMessageResponse
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.UUID

class ChatBroadcastListenerTest : BehaviorSpec() {

    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val sut = ChatBroadcastListener(messagingTemplate)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        val roomUuid = UUID.randomUUID()

        given("채팅 메시지 이벤트") {
            val message = mockk<ChatMessageResponse>(relaxed = true)

            `when`("수신하면") {
                then("방 구독 경로로 메시지를 전송한다") {
                    sut.on(ChatMessageBroadcastEvent(roomUuid = roomUuid, message = message))

                    verify(exactly = 1) { messagingTemplate.convertAndSend("/sub/chat/room/$roomUuid", message) }
                }
            }

            `when`("전송이 실패하면") {
                then("예외를 전파하지 않는다") {
                    every {
                        messagingTemplate.convertAndSend(any<String>(), any<Any>())
                    } throws MessageDeliveryException("closed")

                    shouldNotThrowAny {
                        sut.on(ChatMessageBroadcastEvent(roomUuid = roomUuid, message = message))
                    }
                }
            }
        }

        given("방 이벤트") {
            val event = ChatRoomEvent(
                type = ChatRoomEventType.MEMBER_LEFT,
                roomUuid = roomUuid,
                partyUuid = UUID.randomUUID(),
                actorUserUuid = UUID.randomUUID(),
            )

            `when`("수신하면") {
                then("방 이벤트 구독 경로로 전송한다") {
                    sut.on(event)

                    verify(exactly = 1) { messagingTemplate.convertAndSend("/sub/chat/room/$roomUuid/events", event) }
                }
            }
        }
    }
}
