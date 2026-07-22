package com.dogGetDrunk.meetjyou.chat.connection

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

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

        given("한 방에 여러 유저가 접속했을 때") {
            val sut = ChatSessionTracker()
            val roomUuid = UUID.randomUUID()
            val userA = UUID.randomUUID()
            val userB = UUID.randomUUID()

            `when`("한 유저의 모든 세션이 끊기면") {
                then("getConnectedUsers는 세션이 남아있는 유저만 반환한다") {
                    sut.connectUser(roomUuid, userA, "a1")
                    sut.connectUser(roomUuid, userB, "b1")

                    sut.disconnectUser(roomUuid, userA, "a1")

                    sut.getConnectedUsers(roomUuid) shouldContainExactlyInAnyOrder listOf(userB)
                }
            }
        }

        given("동시성: 마지막 세션 해제와 새 세션 접속이 같은 방/유저에서 경쟁할 때") {
            `when`("한 스레드가 기존 세션을 끊는 동시에 다른 스레드가 새 세션을 붙이면") {
                then("살아있는 새 세션 덕분에 접속 상태가 유지된다 (presence 유실 없음)") {
                    val sut = ChatSessionTracker()
                    val roomUuid = UUID.randomUUID()
                    val userUuid = UUID.randomUUID()
                    val pool = Executors.newFixedThreadPool(2)
                    try {
                        repeat(5000) {
                            sut.connectUser(roomUuid, userUuid, "old")
                            val barrier = CyclicBarrier(2)
                            val disconnect = pool.submit {
                                barrier.await()
                                sut.disconnectUser(roomUuid, userUuid, "old")
                            }
                            val connect = pool.submit {
                                barrier.await()
                                sut.connectUser(roomUuid, userUuid, "new")
                            }
                            disconnect.get()
                            connect.get()

                            // "new" always outlives "old", so the user must remain connected.
                            sut.isUserConnected(roomUuid, userUuid) shouldBe true

                            sut.disconnectAllSessions(roomUuid, userUuid)
                        }
                    } finally {
                        pool.shutdownNow()
                    }
                }
            }
        }
    }
}
