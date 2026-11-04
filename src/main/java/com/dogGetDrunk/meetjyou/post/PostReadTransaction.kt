package com.dogGetDrunk.meetjyou.post

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Runs a block in its own read-only transaction through a separate bean, so a caller in
 * PostService gets a real proxy call instead of a self-invocation that skips the transaction.
 * The connection is returned when the block ends, before the caller does any follow-up write.
 */
@Component
class PostReadTransaction {
    @Transactional(readOnly = true)
    fun <T> run(block: () -> T): T = block()
}
