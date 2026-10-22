package com.dogGetDrunk.meetjyou.support

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.dao.PessimisticLockingFailureException

/**
 * Accepted deadlocks (#165) are handled after the fact: InnoDB rolls one transaction back and
 * GlobalExceptionHandler maps PessimisticLockingFailureException to 409. Asserts exactly one side
 * lost with that exception type, and returns whether it was [first].
 */
fun firstLostDeadlock(first: Result<Unit>, second: Result<Unit>): Boolean {
    (first.isFailure != second.isFailure) shouldBe true
    val (loser, winner) = if (first.isFailure) first to second else second to first
    loser.exceptionOrNull().shouldBeInstanceOf<PessimisticLockingFailureException>()
    winner.exceptionOrNull().shouldBeNull()
    return first.isFailure
}
