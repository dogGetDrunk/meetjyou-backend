package com.dogGetDrunk.meetjyou.auth.refreshtoken

// A legitimate client is at most a hop or two behind the newest token of its chain.
private const val MAX_CHAIN_HOPS = 10

/**
 * Follows replacedByJti from [record] to the newest token of its rotation chain, locking each row
 * in chain order (old to new, the order rotation locks in, so the two cannot deadlock). Returns
 * null when the chain does not end within [MAX_CHAIN_HOPS], so the caller can fail closed.
 */
fun RefreshTokenRepository.findChainHeadForUpdate(record: RefreshToken): RefreshToken? {
    var current = record
    repeat(MAX_CHAIN_HOPS) {
        current = current.replacedByJti?.let { findForUpdateByJti(it) } ?: return current
    }
    return null
}
