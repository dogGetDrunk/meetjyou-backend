package com.dogGetDrunk.meetjyou.support

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Asserts the contender really waited on an InnoDB lock. On failure the clue says how the
 * contender ended (#189), so a run that never reached the lock (e.g. failed early on a lock-free
 * read) can be told apart from a missed detection. Business exceptions carry no stack trace,
 * so the exception message is what tells the code paths apart.
 */
fun assertWaitedOnLock(waited: Boolean, contender: Result<*>) {
    withClue({ "contender ended with $contender" }) { waited shouldBe true }
}
