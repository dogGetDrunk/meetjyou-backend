package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchResponse
import com.dogGetDrunk.meetjyou.support.PlaceDb
import com.dogGetDrunk.meetjyou.support.SbizFixture
import com.dogGetDrunk.meetjyou.support.StoreRow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/**
 * Result contract of the three search paths. "Same results" means the same set of places that qualify
 * (C-r1-02): the fixtures keep every candidate within one page, since order past exact names is free.
 */
class PlaceSearchPathDockerTest : BehaviorSpec() {
    init {
        given("AT-003 영문·불용어 이름과 특수문자 검색어") {
            val db = PlaceDb()
            db.import(SbizFixture.zip(NAMED_STORES))
            val sut = db.searchService()

            QUERIES.forEach { query ->
                then("'$query': 기존 구문 검색과 같은 장소") {
                    ids(sut.search(query, null, MAX_SIZE)) shouldBe legacyIds(db, query, null)
                }
            }
            then("쓸 토큰이 없는 'asia'는 정확히 같은 이름의 장소가 있어도 빈 결과") {
                sut.search("asia", null, MAX_SIZE).places.shouldBeEmpty()
            }
            then("1글자 단어만 남는 '!표'는 지역 인덱스 경로에서도 빈 결과") {
                db.searchService(ALWAYS_REGION_INDEX).search("!표", "41", MAX_SIZE).places.shouldBeEmpty()
            }
        }

        given("AT-004 같은 검색어·지역") {
            val db = PlaceDb()
            db.import(SbizFixture.zip(NAMED_STORES))
            val fullText = db.searchService(ALWAYS_FULL_TEXT)
            val regionIndex = db.searchService(ALWAYS_REGION_INDEX)

            REGION_FILTERS.forEach { region ->
                QUERIES.forEach { query ->
                    then("지역 $region '$query': 두 경로의 결과가 같고 기존 구문 검색과도 같다") {
                        val viaIndex = ids(regionIndex.search(query, region, MAX_SIZE))
                        ids(fullText.search(query, region, MAX_SIZE)) shouldBe viaIndex
                        viaIndex shouldBe legacyIds(db, query, db.regionCatalog().selfAndDescendants(region))
                    }
                }
            }
        }

        given("AT-002 후보가 size보다 많을 때") {
            val db = PlaceDb()
            val cafes = listOf("카페", "카페", "카페", "카페베네", "수원카페", "제주카페", "카페거리")
            db.import(SbizFixture.zip(cafes.mapIndexed { i, name -> StoreRow("C$i", name = name, sigungu = "11680") }))

            listOf(ALWAYS_FULL_TEXT, ALWAYS_REGION_INDEX).forEach { threshold ->
                val sut = db.searchService(threshold)
                listOf(null, "11").forEach { region ->
                    then("정확 일치가 size보다 많으면 정확 일치만 size개 (경로 기준 $threshold, 지역 $region)") {
                        sut.search("카페", region, 2).places.map { it.name } shouldContainExactly listOf("카페", "카페")
                    }
                    then("결과 수 = size, 모두 후보 자격, 중복 없음, 정확 일치 먼저 (경로 기준 $threshold, 지역 $region)") {
                        val places = sut.search("카페", region, 5).places
                        places shouldHaveSize 5
                        places.map { it.id }.distinct() shouldHaveSize 5
                        places.take(3).map { it.name } shouldContainExactly listOf("카페", "카페", "카페")
                        places.forEach { it.name shouldContain "카페" }
                    }
                }
            }
        }

        given("AT-005 적재가 장소를 바꾼 뒤") {
            val db = PlaceDb()
            db.import(SbizFixture.zip(FILE_A))
            val before = db.places().associate { it.sourceKey to it.id }

            db.import(SbizFixture.zip(FILE_B))

            listOf(ALWAYS_FULL_TEXT, ALWAYS_REGION_INDEX).forEach { threshold ->
                val sut = db.searchService(threshold)
                listOf(null, DISTRICT).forEach { region ->
                    then("이름 변경·신규·누락·재매칭이 검색에 반영된다 (경로 기준 $threshold, 지역 $region)") {
                        fun find(query: String) = sut.search(query, region, MAX_SIZE).places
                        find("새이름").map { it.id } shouldContainExactly listOf(before.getValue("RENAMED"))
                        find("옛이름").shouldBeEmpty()
                        find("사라질").shouldBeEmpty()
                        find("새로생긴").map { it.name } shouldContainExactly listOf("새로생긴카페")
                        find("번호바뀔").map { it.id } shouldContainExactly listOf(before.getValue("OLD_KEY"))
                    }
                }
            }
        }
    }

    private fun ids(response: PlaceSearchResponse): Set<Long> =
        response.places.map { it.id }.toSet()

    // The search before this task: one phrase, no LIMIT here because only the qualifying set is compared.
    private fun legacyIds(db: PlaceDb, query: String, regionCodes: Set<String>?): Set<Long> {
        val phrase = "\"${RegionCatalog.compact(query).replace("\"", "")}\""
        val params = MapSqlParameterSource("phrase", phrase)
        val regionFilter = if (regionCodes == null) "" else "AND region_code IN (:regionCodes)"
        if (regionCodes != null) params.addValue("regionCodes", regionCodes)
        val sql = "SELECT id FROM place WHERE MATCH(search_name) AGAINST (:phrase IN BOOLEAN MODE) AND active = 1 $regionFilter"
        return NamedParameterJdbcTemplate(db.dataSource).queryForList(sql, params, Long::class.java).toSet()
    }

    private companion object {
        const val MAX_SIZE = 50
        const val DISTRICT = "11680"

        // fullTextMaxMatches pinning a region filter to one path:
        // no count exceeds MAX_VALUE, every count exceeds -1.
        const val ALWAYS_FULL_TEXT = Long.MAX_VALUE
        const val ALWAYS_REGION_INDEX = -1L

        val NAMED_STORES = listOf(
            StoreRow("PIZZA", name = "PizzaHut", sigungu = "11680"),
            StoreRow("ASIAMART", name = "Asia Mart", sigungu = "41111"),
            StoreRow("ASIA", name = "asia", sigungu = "11680"),
            StoreRow("GS25", name = "GS25", branch = "강남역점", sigungu = "11680"),
            StoreRow("PERCENT", name = "50%할인마트", sigungu = "41113"),
            StoreRow("UNDERSCORE", name = "언더_바", sigungu = "11650"),
            StoreRow("BANG", name = "느낌!표", sigungu = "41111"),
            StoreRow("CAFE", name = "카페", sigungu = "11680"),
            StoreRow("BENE", name = "카페베네", sigungu = "41111"),
            StoreRow("JEJU", name = "제주카페", sigungu = "50110"),
            StoreRow("PLAIN", name = "언더바", sigungu = "11680"),
        )
        val QUERIES = listOf(
            "pizzahut", "PIZZA", "hut", "asiamart", "Asia Mart", "gs25", "GS25강남", "50%", "0%할",
            "_바", "언더바", "!표", "카페", "카\"페", "카페베네",
        )
        val REGION_FILTERS = listOf("11", "11680", "41110", "41")

        val FILE_A = listOf(
            StoreRow("RENAMED", name = "옛이름카페", sigungu = DISTRICT, address = "도로명 1"),
            StoreRow("GONE", name = "사라질카페", sigungu = DISTRICT, address = "도로명 2"),
            StoreRow("OLD_KEY", name = "번호바뀔카페", sigungu = DISTRICT, address = "도로명 3"),
        )
        val FILE_B = listOf(
            StoreRow("RENAMED", name = "새이름카페", sigungu = DISTRICT, address = "도로명 1"),
            StoreRow("NEW_KEY", name = "번호바뀔카페", sigungu = DISTRICT, address = "도로명 3"),
            StoreRow("ADDED1", name = "새로생긴카페", sigungu = DISTRICT, address = "도로명 4"),
            StoreRow("ADDED2", name = "또다른가게", sigungu = DISTRICT, address = "도로명 5"),
        )
    }
}
