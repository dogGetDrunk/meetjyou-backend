package com.dogGetDrunk.meetjyou.common.exception

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.sql.SQLException
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException

class UniqueConstraintConflictsTest : BehaviorSpec({

    fun violation(constraintName: String) = DataIntegrityViolationException(
        "could not execute statement",
        ConstraintViolationException("Duplicate entry", SQLException("Duplicate entry"), constraintName),
    )

    given("#247 경합 경로의 유니크 제약 위반이면") {
        listOf(
            "push_token.uk_push_token_token",
            "chat_participant.uk_chat_participant_user_room",
            "comp_preference.uq_comp_preference",
        ).forEach { constraint ->
            `when`("$constraint 이면") {
                then("AT-2-1 AT-3-1 AT-4-2 409 CONCURRENT_REQUEST_CONFLICT로 매핑한다") {
                    UniqueConstraintConflicts.errorCodeFor(violation(constraint)) shouldBe
                        ErrorCode.CONCURRENT_REQUEST_CONFLICT
                }
            }
        }
    }

    given("V42 이전의 자동 생성 인덱스 이름이면") {
        listOf("push_token.token", "chat_participant.user_id").forEach { constraint ->
            `when`("$constraint 이면") {
                then("범용 이름은 매핑하지 않는다") {
                    UniqueConstraintConflicts.errorCodeFor(violation(constraint)).shouldBeNull()
                }
            }
        }
    }
})
