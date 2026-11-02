package com.dogGetDrunk.meetjyou.chat

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.sql.SQLException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

class ChatControllerExceptionTest : BehaviorSpec({

    val sut = ChatController(mockk())

    fun violation(constraintName: String) = DataIntegrityViolationException(
        "could not execute statement",
        ConstraintViolationException("Duplicate entry", SQLException("Duplicate entry"), constraintName),
    )

    given("STOMP 메시지 처리 중 유니크 제약 위반이 나면") {
        `when`("같은 clientMessageId 재전송이 원본과 경합해 졌으면") {
            then("AT-5-1 에러 프레임 없이 무시한다") {
                sut.handleDataIntegrityViolation(violation("chat_message.uk_chat_message_room_sender_client_id"))
                    .shouldBeNull()
            }
        }

        `when`("다른 제약 위반이면") {
            then("AT-5-1 기존처럼 INTERNAL_SERVER_ERROR를 보낸다") {
                val response = sut.handleDataIntegrityViolation(violation("chat_message.other")).shouldNotBeNull()

                response.errorCode shouldBe ErrorCode.INTERNAL_SERVER_ERROR.name
            }
        }
    }
})
