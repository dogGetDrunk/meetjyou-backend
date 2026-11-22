package com.dogGetDrunk.meetjyou.place.tour

import com.dogGetDrunk.meetjyou.place.Region
import com.dogGetDrunk.meetjyou.place.RegionCatalog
import com.dogGetDrunk.meetjyou.place.RegionLevel
import com.dogGetDrunk.meetjyou.place.RegionRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/** AT-002 (type filter, region mapping, region filter, coordinates) and AT-003 unit part (failure, disabled). */
class TourPlaceSearchTest : BehaviorSpec() {

    private class FakeSource(
        override val enabled: Boolean = true,
        private val answer: () -> List<TourPlace> = { emptyList() },
    ) : TourPlaceSource {
        var calls = 0
        var lastSido: String? = null

        override fun searchKeyword(keyword: String, sidoCode: String?, size: Int): List<TourPlace> {
            calls++
            lastSido = sidoCode
            return answer()
        }
    }

    private val catalog = RegionCatalog(
        mockk<RegionRepository> {
            every { findAll() } returns listOf(
                Region(Region.UNSPECIFIED_CODE, "미지정", "미지정", RegionLevel.SIDO, null, false),
                Region(SEOUL, "서울특별시", "서울특별시", RegionLevel.SIDO, null, true),
                Region(JONGNO, "종로구", "서울특별시 종로구", RegionLevel.SIGUNGU, SEOUL, true),
                Region(GYEONGGI, "경기도", "경기도", RegionLevel.SIDO, null, true),
                Region(SUWON, "수원시", "경기도 수원시", RegionLevel.SIGUNGU, GYEONGGI, true),
                Region(JANGAN, "수원시 장안구", "경기도 수원시 장안구", RegionLevel.SIGUNGU, SUWON, true),
            )
        },
    )

    // xy = (mapx, mapy) = (longitude, latitude), as the API names them.
    private fun place(
        id: String = "1",
        type: String = "12",
        regn: String = SEOUL,
        signgu: String = "110",
        xy: Pair<String, String> = "126.97" to "37.57",
    ) = TourPlace(id, type, "이름$id", "주소", xy.first, xy.second, regn, signgu)

    private fun search(source: FakeSource, region: String? = null) =
        TourPlaceSearch(source, catalog).search(" 경복궁 ", region, SIZE)

    init {
        given("관광공사가 응답할 때") {
            then("관광지, 문화시설, 레포츠만 남기고 분류 이름을 붙인다") {
                val types = listOf("12", "14", "28", "32", "38", "39", "15", "25")
                val result = search(FakeSource { types.map { place(id = it, type = it) } })
                result.status shouldBe TourSearchStatus.OK
                result.places.map { it.contentId } shouldBe listOf("12", "14", "28")
                result.places.map { it.categoryName } shouldBe listOf("관광지", "문화시설", "레포츠")
            }
            then("시·도+시·군·구 코드가 우리 지역이면 그 지역, 모르는 시·군·구면 시·도, 모르는 시·도면 버린다") {
                val result = search(
                    FakeSource {
                        listOf(place(id = "a"), place(id = "b", signgu = "999"), place(id = "c", regn = "99"))
                    },
                )
                result.places.map { it.contentId to it.region.code } shouldBe listOf("a" to JONGNO, "b" to SEOUL)
            }
            then("좌표가 숫자가 아니거나 0이면 버린다") {
                val result = search(
                    FakeSource {
                        listOf(
                            place(id = "ok"),
                            place(id = "x", xy = "" to "37.57"),
                            place(id = "y", xy = "126.97" to "0"),
                        )
                    },
                )
                result.places.map { it.contentId } shouldBe listOf("ok")
                result.places[0].lat shouldBe 37.57
                result.places[0].lng shouldBe 126.97
            }
            then("0건이어도 OK") {
                search(FakeSource()).status shouldBe TourSearchStatus.OK
            }
        }

        given("지역 필터가 있을 때") {
            then("API에는 시·도만 넘기고 결과는 일반구까지 포함해 다시 거른다") {
                val source = FakeSource {
                    listOf(place(id = "jangan", regn = GYEONGGI, signgu = "111"), place(id = "jongno"))
                }
                val result = search(source, region = SUWON)
                source.lastSido shouldBe GYEONGGI
                result.places.map { it.contentId } shouldBe listOf("jangan")
            }
        }

        given("관광공사가 실패할 때") {
            then("TourApiException이면 FAILED와 빈 목록") {
                val result = search(FakeSource { throw TourApiException(TourApiException.Reason.UNREACHABLE, "x") })
                result.status shouldBe TourSearchStatus.FAILED
                result.places.shouldBeEmpty()
            }
            then("예상하지 못한 예외도 FAILED") {
                search(FakeSource { throw IllegalStateException("boom") }).status shouldBe TourSearchStatus.FAILED
            }
        }

        given("서비스키가 없을 때") {
            then("호출하지 않고 DISABLED") {
                val source = FakeSource(enabled = false)
                search(source).status shouldBe TourSearchStatus.DISABLED
                source.calls shouldBe 0
            }
        }
    }

    private companion object {
        const val SIZE = 20
        const val SEOUL = "11"
        const val JONGNO = "11110"
        const val GYEONGGI = "41"
        const val SUWON = "41110"
        const val JANGAN = "41111"
    }
}
