package com.dogGetDrunk.meetjyou.chat.event

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class ChatRoomEventBroadcasterTest : BehaviorSpec() {

    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val sut = ChatRoomEventBroadcaster(publisher)

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        beforeEach { clearAllMocks() }

        given("방 이벤트 브로드캐스트") {
            val roomUuid = UUID.randomUUID()
            val partyUuid = UUID.randomUUID()
            val userUuid = UUID.randomUUID()

            `when`("멤버 탈퇴를 알리면") {
                then("직접 전송하지 않고 커밋 후 전송용 이벤트를 발행한다") {
                    sut.broadcastMemberLeft(roomUuid, partyUuid, userUuid)

                    verify(exactly = 1) {
                        publisher.publishEvent(
                            match<Any> {
                                it is ChatRoomEvent &&
                                    it.type == ChatRoomEventType.MEMBER_LEFT &&
                                    it.roomUuid == roomUuid &&
                                    it.targetUserUuid == userUuid
                            }
                        )
                    }
                }
            }
        }
    }
}
