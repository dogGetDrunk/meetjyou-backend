package com.dogGetDrunk.meetjyou.place

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.atomic.AtomicReference

/**
 * Active place count per region, used only to pick the cheaper search path. Places change only through
 * an import, so the counts are read on first use and dropped after a successful import. Stale counts
 * pick a slower path, never wrong results.
 */
@Component
class PlaceRegionStats(
    private val jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) {
    // Its own read-only transaction: inside a search transaction that began before an import committed,
    // the count would come from the pre-import snapshot and be published under the new generation.
    private val freshRead = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }

    // A new Generation per invalidate(): a read that started before it cannot store pre-import counts,
    // which would otherwise stay until the next quarterly import.
    private val current = AtomicReference(Generation())

    fun activeCount(regionCodes: Set<String>): Long {
        val generation = current.get()
        val snapshot = generation.counts ?: load(generation)
        return regionCodes.sumOf { snapshot[it] ?: 0L }
    }

    fun invalidate() {
        current.set(Generation())
    }

    // A failed read leaves no snapshot behind, so the next search retries it.
    private fun load(generation: Generation): Map<String, Long> {
        val loaded = freshRead.execute { jdbc.query(COUNT_SQL) { rs, _ -> rs.getString(1) to rs.getLong(2) }.toMap() }
            ?: error("Region count query returned no result")
        current.compareAndSet(generation, Generation(loaded))
        return loaded
    }

    private class Generation(val counts: Map<String, Long>? = null)

    companion object {
        private const val COUNT_SQL = "SELECT region_code, COUNT(*) FROM place WHERE active = 1 GROUP BY region_code"
    }
}
