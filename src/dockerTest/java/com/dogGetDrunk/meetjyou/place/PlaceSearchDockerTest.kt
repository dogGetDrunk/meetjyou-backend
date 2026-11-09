package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.support.PlaceDb
import com.dogGetDrunk.meetjyou.support.SbizFixture
import com.dogGetDrunk.meetjyou.support.StoreRow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/** AT-008: ngram full-text search, region filters and ordering on real MySQL. */
class PlaceSearchDockerTest : BehaviorSpec() {
    private val db = PlaceDb()
    private val sut by lazy {
        PlaceSearchService(db.regionCatalog(), PlaceSearchRepository(NamedParameterJdbcTemplate(db.dataSource)))
    }

    init {
        beforeSpec {
            db.import(
                SbizFixture.zip(
                    listOf(
                        StoreRow("MILBON", name = "밀본 그레이츠", sigungu = "11680"),
                        StoreRow("BENE", name = "카페베네", sigungu = "11680"),
                        StoreRow("CAFE", name = "카페", sigungu = "41111"),
                        StoreRow("SUWON", name = "수원카페", sigungu = "41111"),
                        StoreRow("JEJU", name = "제주카페", sigungu = "50110"),
                    ),
                ),
            )
            // An inactive place: imported once, then left out of the next file.
            db.jdbc.update("UPDATE place SET active = 0 WHERE source_key = 'JEJU'")
        }

        given("띄어쓰기만 다른 검색어") {
            then("같은 장소를 찾는다") {
                sut.search("밀본 그레이츠", null, 20).places.map { it.name } shouldContainExactly listOf("밀본 그레이츠")
                sut.search("밀본그레이츠", null, 20).places.map { it.name } shouldContainExactly listOf("밀본 그레이츠")
                sut.search("본그레", null, 20).places.map { it.name } shouldContainExactly listOf("밀본 그레이츠")
            }
        }
        given("지역 필터") {
            then("시는 하위 일반구 장소까지, 시·도는 전체, 다른 시·도는 제외") {
                sut.search("카페", "41110", 20).places.map { it.name } shouldContainExactlyInAnyOrder listOf("카페", "수원카페")
                sut.search("카페", "41", 20).places.map { it.name } shouldContainExactlyInAnyOrder listOf("카페", "수원카페")
                sut.search("카페", "11", 20).places.map { it.name } shouldContainExactly listOf("카페베네")
            }
        }
        given("검색 결과") {
            then("비활성 장소는 나오지 않고, 이름이 정확히 같은 장소가 먼저 나온다") {
                val names = sut.search("카페", null, 20).places.map { it.name }
                names shouldNotContain "제주카페"
                names.first() shouldBe "카페"
                names shouldContainExactlyInAnyOrder listOf("카페", "카페베네", "수원카페")
            }
            then("size만큼만 돌려준다") {
                sut.search("카페", null, 2).places.size shouldBe 2
            }
            then("일반구 장소에는 상위 시와 시·도가 함께 실린다") {
                val region = sut.search("수원카페", null, 20).places.single().region
                region.code shouldBe "41111"
                region.cityCode shouldBe "41110"
                region.cityName shouldBe "수원시"
                region.sidoCode shouldBe "41"
                region.sidoName shouldBe "경기도"
            }
        }
        given("지역 검색") {
            then("이름이 맞는 지역을 함께 돌려주고 미지정은 나오지 않는다") {
                val regions = sut.search("수원", null, 20).regions.map { it.code }
                regions shouldContain "41110"
                regions shouldContain "41111"
                sut.search("미지정", null, 20).regions shouldBe emptyList()
                sut.search("서울특별시", null, 20).regions.first().code shouldBe "11"
            }
        }
    }
}
