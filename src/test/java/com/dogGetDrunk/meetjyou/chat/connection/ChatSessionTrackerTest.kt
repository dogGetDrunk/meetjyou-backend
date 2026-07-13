package com.dogGetDrunk.meetjyou.chat.connection

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ChatSessionTrackerTest : BehaviorSpec() {

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        given("한 유저가 여러 기기(세션)로 같은 방에 접속했을 때") {
            val sut = ChatSessionTracker()
            val roomUuid = UUID.randomUUID()
            val userUuid = UUID.randomUUID()

            `when`("한쪽 세션만 끊기면") {
                then("다른 세션이 살아있는 한 접속 상태로 유지된다") {
                    sut.connectUser(roomUuid, userUuid, "session-web")
                    sut.connectUser(roomUuid, userUuid, "session-mobile")

                    sut.disconnectUser(roomUuid, userUuid, "session-web")

                    sut.isUserConnected(roomUuid, userUuid) shouldBe true
                }
            }

            `when`("모든 세션이 끊기면") {
                then("접속 해제 상태가 된다") {
                    sut.connectUser(roomUuid, userUuid, "session-web")
                    sut.connectUser(roomUuid, userUuid, "session-mobile")

                    sut.disconnectUser(roomUuid, userUuid, "session-web")
                    sut.disconnectUser(roomUuid, userUuid, "session-mobile")

                    sut.isUserConnected(roomUuid, userUuid) shouldBe false
                }
            }

            `when`("disconnectAllSessions을 호출하면") {
                then("세션 개수와 무관하게 즉시 접속 해제 상태가 된다") {
                    sut.connectUser(roomUuid, userUuid, "session-web")
                    sut.connectUser(roomUuid, userUuid, "session-mobile")

                    sut.disconnectAllSessions(roomUuid, userUuid)

                    sut.isUserConnected(roomUuid, userUuid) shouldBe false
                }
            }
        }
    }
}
