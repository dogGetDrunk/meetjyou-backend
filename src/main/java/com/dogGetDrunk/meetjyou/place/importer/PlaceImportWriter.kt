package com.dogGetDrunk.meetjyou.place.importer

import com.dogGetDrunk.meetjyou.place.PlaceSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

/** What one apply changed in `place`. */
data class ApplyCounts(
    val updated: Long,
    val rematched: Long,
    val inserted: Long,
    val deactivated: Long,
)

/**
 * Stages filtered rows, checks the staged set is a whole file, then applies it to `place` in short
 * transactions. Deactivation runs last and only after every other step succeeded, so a failed run
 * never hides stores that are still open. Re-running the same file converges to the same state.
 * MySQL only (multi-table UPDATE, LEFT() on codes): covered by dockerTest.
 */
class PlaceImportWriter(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val chunkSize: Int,
) {
    private val source = PlaceSource.SBIZ.name

    fun clearStaging() {
        jdbc.execute("TRUNCATE TABLE place_import_staging")
    }

    fun stage(rows: List<StagingRow>) {
        val args = rows.map {
            arrayOf<Any?>(
                it.sourceKey, it.name, it.branchName, it.searchName, it.categoryCode, it.categoryName,
                it.regionCode, it.sidoCode, it.roadAddress, it.lat, it.lng,
            )
        }
        jdbc.batchUpdate(STAGE_SQL, args)
    }

    fun stagedCount(): Long = jdbc.queryForObject("SELECT COUNT(*) FROM place_import_staging", Long::class.java) ?: 0L

    /**
     * Rejects a staged set that cannot be a whole file: empty, or any 시·도 that has active places
     * staging under half of them (a missing or truncated regional CSV).
     */
    fun validateCompleteness() {
        if (stagedCount() == 0L) throw PlaceImportException("No store passed the filter")
        val short = jdbc.queryForList(SHORT_SIDO_SQL, String::class.java, source)
        if (short.isNotEmpty()) {
            throw PlaceImportException("Staged rows under half of the active places in 시·도 $short")
        }
    }

    fun apply(): ApplyCounts {
        val updated = forEachStagingChunk { from, to -> jdbc.update(UPDATE_SAME_KEY_SQL, source, from, to) }
        val rematched = rematch()
        val inserted = forEachStagingChunk { from, to -> jdbc.update(INSERT_NEW_SQL, source, from, to, source) }
        val deactivated = forEachPlaceChunk { from, to -> jdbc.update(DEACTIVATE_SQL, source, from, to) }
        return ApplyCounts(updated, rematched, inserted, deactivated)
    }

    /**
     * Same store, new key: a remaining place (key not staged) and a remaining staged row (key not in
     * place) with the same district, search name and road address, one on each side only.
     */
    private fun rematch(): Long {
        val candidates = jdbc.query(REMATCH_CANDIDATES_SQL, { rs, _ -> rs.getLong(1) to rs.getLong(2) }, source, source)
        val placeCounts = candidates.groupingBy { it.first }.eachCount()
        val stagingCounts = candidates.groupingBy { it.second }.eachCount()
        val pairs = candidates.filter { placeCounts[it.first] == 1 && stagingCounts[it.second] == 1 }
        // Counted from the pairs: batch results can be SUCCESS_NO_INFO when the driver rewrites batches.
        pairs.chunked(chunkSize).forEach { chunk ->
            tx.executeWithoutResult { jdbc.batchUpdate(REMATCH_SQL, chunk.map { arrayOf<Any>(it.second, it.first) }) }
        }
        return pairs.size.toLong()
    }

    private fun forEachStagingChunk(work: (Long, Long) -> Int): Long =
        forEachRange("SELECT MIN(id), MAX(id) FROM place_import_staging", work)

    private fun forEachPlaceChunk(work: (Long, Long) -> Int): Long =
        forEachRange("SELECT MIN(id), MAX(id) FROM place", work)

    // MIN/MAX of an empty table read as (0, 0): the loop below then runs no chunk.
    private fun forEachRange(rangeSql: String, work: (Long, Long) -> Int): Long {
        val (min, max) = jdbc.queryForObject(rangeSql) { rs, _ -> rs.getLong(1) to rs.getLong(2) } ?: (1L to 0L)
        var total = 0L
        var from = min
        while (max > 0 && from <= max) {
            val to = from + chunkSize - 1
            total += tx.execute { work(from, to) } ?: 0
            from = to + 1
        }
        return total
    }

    private companion object {
        const val STAGE_SQL = """
            INSERT INTO place_import_staging
                (source_key, name, branch_name, search_name, category_code, category_name, region_code, sido_code,
                 road_address, lat, lng)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE source_key = source_key
        """

        const val SHORT_SIDO_SQL = """
            SELECT a.sido
            FROM (SELECT LEFT(region_code, 2) AS sido, COUNT(*) AS cnt
                  FROM place WHERE source = ? AND active = 1 GROUP BY LEFT(region_code, 2)) a
            LEFT JOIN (SELECT sido_code AS sido, COUNT(*) AS cnt FROM place_import_staging GROUP BY sido_code) s
                ON s.sido = a.sido
            WHERE COALESCE(s.cnt, 0) * 2 < a.cnt
            ORDER BY a.sido
        """

        // Only rows whose values differ (or that come back) are written, so an unchanged re-import
        // updates nothing and keeps updated_at.
        const val UPDATE_SAME_KEY_SQL = """
            UPDATE place p JOIN place_import_staging s ON p.source = ? AND p.source_key = s.source_key
            SET p.name = s.name, p.branch_name = s.branch_name, p.search_name = s.search_name,
                p.category_code = s.category_code, p.category_name = s.category_name, p.region_code = s.region_code,
                p.road_address = s.road_address, p.lat = s.lat, p.lng = s.lng, p.active = 1
            WHERE s.id BETWEEN ? AND ?
              AND (p.active = 0 OR p.name <> s.name OR NOT (p.branch_name <=> s.branch_name)
                   OR p.category_code <> s.category_code OR p.category_name <> s.category_name
                   OR p.region_code <> s.region_code OR p.road_address <> s.road_address
                   OR p.lat <> s.lat OR p.lng <> s.lng)
        """

        const val REMATCH_CANDIDATES_SQL = """
            SELECT p.id, s.id
            FROM place p
            JOIN place_import_staging s
                ON s.region_code = p.region_code AND s.search_name = p.search_name AND s.road_address = p.road_address
            WHERE p.source = ?
              AND NOT EXISTS (SELECT 1 FROM place_import_staging s2 WHERE s2.source_key = p.source_key)
              AND NOT EXISTS (SELECT 1 FROM place p2 WHERE p2.source = ? AND p2.source_key = s.source_key)
        """

        const val REMATCH_SQL = """
            UPDATE place p JOIN place_import_staging s ON s.id = ?
            SET p.source_key = s.source_key, p.name = s.name, p.branch_name = s.branch_name,
                p.search_name = s.search_name, p.category_code = s.category_code, p.category_name = s.category_name,
                p.region_code = s.region_code, p.road_address = s.road_address, p.lat = s.lat, p.lng = s.lng,
                p.active = 1
            WHERE p.id = ?
        """

        const val INSERT_NEW_SQL = """
            INSERT INTO place
                (source, source_key, name, branch_name, search_name, category_code, category_name, region_code,
                 road_address, lat, lng, active)
            SELECT ?, s.source_key, s.name, s.branch_name, s.search_name, s.category_code, s.category_name,
                   s.region_code, s.road_address, s.lat, s.lng, 1
            FROM place_import_staging s
            WHERE s.id BETWEEN ? AND ?
              AND NOT EXISTS (SELECT 1 FROM place p WHERE p.source = ? AND p.source_key = s.source_key)
        """

        const val DEACTIVATE_SQL = """
            UPDATE place p SET p.active = 0
            WHERE p.source = ? AND p.active = 1 AND p.id BETWEEN ? AND ?
              AND NOT EXISTS (SELECT 1 FROM place_import_staging s WHERE s.source_key = p.source_key)
        """
    }
}
