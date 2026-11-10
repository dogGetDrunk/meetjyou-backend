package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.support.PlaceDb
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import kotlin.random.Random

/**
 * AT-001: the rows one search reads must not grow with the number of matching places.
 *
 * Two databases differ only in how many places match (N vs 10N); filler rows, region split and the
 * inactive share stay the same. Rows go in one region after another like the production import (one
 * 시·도 file after another), with the big region last: there, full-text with the region filter walks every
 * other region's matches first (production 서울 '카페': about 19k rows, 0.56 s).
 *
 * "Rows read" = ROWS_EXAMINED of the search's statements (counts the full-text row fetches) + Handler_read_next (counts region-index entries returned after the index
 * condition, which ROWS_EXAMINED misses, e.g. a Multi-Range Read collecting every match). Both come from
 * the search's own connection, so other specs on the shared server don't leak in.
 *
 * Region-index entries rejected by the index condition are counted by neither: a word sparse in the
 * region scans the region's entries, bounded by the region's size, not by the match count (the accepted
 * worst case of PlaceSearchService.FULL_TEXT_MAX_MATCHES).
 * Multi-Range Read only shows at production scale (it reads up to its buffer, then stops), so besides
 * the 10x comparison every search must stay within a few pages of rows.
 */
class PlaceSearchCostDockerTest : BehaviorSpec() {
    private val small = CostDb(MATCHES)
    private val large = CostDb(MATCHES * GROWTH)

    init {
        given("일치 장소가 10배인 데이터") {
            CASES.forEach { case ->
                then("${case.label}: 읽는 행 수가 늘지 않는다") {
                    val before = small.rowsRead(case)
                    val after = large.rowsRead(case)
                    after.rows shouldBeLessThanOrEqual (before.rows * TOLERANCE).toInt() + SLACK
                    after.rows shouldBeLessThanOrEqual MAX_ROWS_PER_SEARCH
                    after.places shouldBe before.places
                }
            }
            then("보조 지표: 추가 일치 1건당 논리 페이지 읽기 0.1 미만") {
                val extraMatches = (MATCHES * GROWTH - MATCHES).toDouble()
                CASES.forEach { case ->
                    val growth = large.minPageReads(case) - small.minPageReads(case)
                    (growth / extraMatches < MAX_PAGES_PER_MATCH) shouldBe true
                }
            }
        }
    }

    private data class Case(val label: String, val query: String, val region: String?)

    private data class Cost(val rows: Int, val places: Int)

    private class CostDb(matches: Int) {
        val db = PlaceDb()
        private val connection = db.mysql.rootConnection()
        private val dataSource = SingleConnectionDataSource(connection, true)
        private val jdbc = JdbcTemplate(dataSource)
        private val sut = PlaceSearchService(
            db.regionCatalog(),
            PlaceSearchRepository(NamedParameterJdbcTemplate(dataSource)),
            FULL_TEXT_MAX,
        )

        init {
            // MySQL picks Multi-Range Read by cost, and only at production scale (a 28k-row 시·도 in the
            // task's local reproduction). Make it always eligible so the region-index path must opt out.
            jdbc.execute("SET SESSION optimizer_switch = 'mrr_cost_based=off'")
            insertPlaces(matches)
        }

        fun rowsRead(case: Case): Cost {
            val marker = lastEventId()
            val readNext = handlerReadNext()
            val places = sut.search(case.query, case.region, PAGE_SIZE).places.size
            val examined = jdbc.queryForObject(
                """SELECT COALESCE(SUM(ROWS_EXAMINED), 0) FROM performance_schema.events_statements_history
                   WHERE THREAD_ID = PS_CURRENT_THREAD_ID() AND EVENT_ID > ? AND SQL_TEXT LIKE '%FROM place%'""",
                Int::class.java, marker,
            ) ?: 0
            return Cost(examined + handlerReadNext() - readNext, places)
        }

        fun minPageReads(case: Case): Long = (1..PAGE_RUNS).minOf {
            val before = pageReadRequests()
            sut.search(case.query, case.region, PAGE_SIZE)
            pageReadRequests() - before
        }

        private fun lastEventId(): Long = jdbc.queryForObject(
            "SELECT COALESCE(MAX(EVENT_ID), 0) FROM performance_schema.events_statements_history WHERE THREAD_ID = PS_CURRENT_THREAD_ID()",
            Long::class.java,
        ) ?: 0L

        private fun handlerReadNext(): Int =
            jdbc.queryForList("SHOW SESSION STATUS LIKE 'Handler_read_next'").single()["Value"].toString().toInt()

        private fun pageReadRequests(): Long =
            jdbc.queryForList("SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_read_requests'").single()["Value"]
                .toString().toLong()

        // Within a region matches are shuffled in among the filler; regions go in by FILE_ORDER.
        private fun insertPlaces(matches: Int) {
            val random = Random(SEED)
            val names = List(matches) { "${word(random)}카페" } + List(matches) { "스타벅스${word(random)}" } +
                List(FILLER) { word(random) }
            val rows = names.shuffled(random).mapIndexed { i, name ->
                listOf("K$i", name, name, pickRegion(random), if (random.nextInt(INACTIVE_ONE_IN) == 0) 0 else 1)
            }.sortedBy { row -> FILE_ORDER.indexOfFirst { row[REGION_COLUMN] in it } }
            // Multi-row INSERTs keep tens of thousands of rows to a few round trips.
            rows.chunked(INSERT_CHUNK).forEach { chunk ->
                val values = chunk.joinToString(",") { ROW_VALUES }
                db.jdbc.update("$INSERT_PREFIX $values", *chunk.flatten().toTypedArray())
            }
        }

        private fun pickRegion(random: Random): String {
            val roll = random.nextInt(PERCENT)
            return when {
                roll < SMALL_REGION_PERCENT -> SMALL_REGION
                roll < SMALL_REGION_PERCENT * 2 -> SMALL_SIDO_DISTRICTS.random(random)
                roll < SMALL_REGION_PERCENT * 2 + BIG_REGION_PERCENT -> BIG_REGION_DISTRICTS.random(random)
                else -> OTHER_DISTRICTS.random(random)
            }
        }

        // Syllables chosen so a filler word never contains 카페, 스타벅스 or the rare query.
        private fun word(random: Random): String = (1..WORD_LENGTH).joinToString("") { SYLLABLES.random(random).toString() }
    }

    companion object {
        private const val MATCHES = 2_000
        private const val GROWTH = 10
        private const val FILLER = 20_000
        private const val PAGE_SIZE = 20
        private const val TOLERANCE = 1.2
        private const val SLACK = 20
        // A page of results read once, plus the exact-name lookup and a few rows of measurement overhead.
        private const val MAX_ROWS_PER_SEARCH = PAGE_SIZE * 3
        private const val MAX_PAGES_PER_MATCH = 0.1
        private const val PAGE_RUNS = 3
        // Below MATCHES, so the common words take the region-index path in both databases.
        private const val FULL_TEXT_MAX = 1_000L
        private const val INACTIVE_ONE_IN = 10
        private const val PERCENT = 100
        private const val SMALL_REGION_PERCENT = 4
        private const val BIG_REGION_PERCENT = 66
        private const val WORD_LENGTH = 3
        private const val INSERT_CHUNK = 1_000
        private const val SEED = 7
        private const val SYLLABLES = "가나다라마바사아자차타하고노도로모보소오조초토호구누두루무부수우주추투후"
        private const val SMALL_REGION = "47940"
        private val BIG_REGION_DISTRICTS = listOf("11680", "11650", "11440", "11110")
        private val OTHER_DISTRICTS = listOf("26350", "41111")

        // A small 시·도 with two districts: several index ranges, where MySQL would pick Multi-Range Read.
        private const val SMALL_SIDO = "50"
        private val SMALL_SIDO_DISTRICTS = listOf("50110", "50130")
        private val FILE_ORDER =
            listOf(OTHER_DISTRICTS, SMALL_SIDO_DISTRICTS, listOf(SMALL_REGION), BIG_REGION_DISTRICTS)
        private const val REGION_COLUMN = 3
        private const val INSERT_PREFIX = """
            INSERT INTO place (source, source_key, name, search_name, category_code, category_name,
                               region_code, road_address, lat, lng, active) VALUES
        """
        private const val ROW_VALUES = "('SBIZ', ?, ?, ?, 'I21201', '카페', ?, '도로명', 37.5, 127.0, ?)"

        private val CASES = listOf(
            Case("전국 2글자", "카페", null),
            Case("전국 여러 글자", "스타벅스", null),
            Case("큰 지역(파일 맨 뒤) 흔한 단어 → 지역 인덱스 경로", "카페", "11"),
            Case("큰 지역 희귀어 → FTS 경로, 결과 0건", "희귀한이름", "11"),
            Case("작은 지역 흔한 단어 → 지역 인덱스 경로", "카페", SMALL_REGION),
            Case("작은 시·도(지역 코드 여러 개) 흔한 단어 → 지역 인덱스 경로", "카페", SMALL_SIDO),
            Case("작은 지역 희귀어 → FTS 경로, 결과 0건", "희귀한이름", SMALL_REGION),
        )
    }
}
