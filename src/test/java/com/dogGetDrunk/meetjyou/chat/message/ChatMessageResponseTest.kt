package com.dogGetDrunk.meetjyou.chat.message

import com.dogGetDrunk.meetjyou.chat.room.ChatRoom
import com.dogGetDrunk.meetjyou.notificationcenter.support.NotificationCenterFixtures
import com.dogGetDrunk.meetjyou.user.User
import com.dogGetDrunk.meetjyou.user.dto.UserDisplay
import com.dogGetDrunk.meetjyou.user.support.UserFixtures
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

// AT-003 (unit): chat sender follows the shared display rule, masked only once purged.
class ChatMessageResponseTest : BehaviorSpec({

    fun messageFrom(sender: User): ChatMessage =
        ChatMessage(room = ChatRoom(party = NotificationCenterFixtures.party()), sender = sender, body = "hi")

    given("채팅 메시지 응답을 만들 때") {
        `when`("발신자가 정상(NORMAL) 회원이면") {
            then("원래 닉네임과 isWithdrawn=false를 담는다") {
                val response = ChatMessageResponse.of(messageFrom(UserFixtures.user(nickname = NICKNAME)), 0)

                response.senderNickname shouldBe NICKNAME
                response.isWithdrawn shouldBe false
            }
        }

        `when`("발신자가 탈퇴 유예 기간 중(DELETED)이면") {
            then("아직 가리지 않는다") {
                val sender = UserFixtures.user(nickname = NICKNAME).apply { withdraw(Instant.now()) }

                val response = ChatMessageResponse.of(messageFrom(sender), 0)

                response.senderNickname shouldBe NICKNAME
                response.isWithdrawn shouldBe false
            }
        }

        `when`("발신자가 완전 탈퇴(PURGED)했으면") {
            then("탈퇴 문구로 가리고 isWithdrawn=true를 담는다") {
                val sender = UserFixtures.user(nickname = NICKNAME).apply {
                    withdraw(Instant.now())
                    anonymize()
                }

                val response = ChatMessageResponse.of(messageFrom(sender), 0)

                response.senderUuid shouldBe sender.uuid.toString()
                response.senderNickname shouldBe UserDisplay.WITHDRAWN_NICKNAME
                response.isWithdrawn shouldBe true
            }
        }
    }
}) {
    companion object {
        private const val NICKNAME = "sender1"
    }
}
