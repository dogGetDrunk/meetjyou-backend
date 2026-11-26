package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Optional

// AT-010
class TravelDestinationResolverTest : BehaviorSpec() {
    private val regions = listOf(
        Region("00", "미지정", "미지정", RegionLevel.SIDO, null, false),
        Region("11", "서울특별시", "서울특별시", RegionLevel.SIDO, null, true),
        Region("11680", "강남구", "서울특별시 강남구", RegionLevel.SIGUNGU, "11", true),
        Region("41", "경기도", "경기도", RegionLevel.SIDO, null, true),
        Region("41110", "수원시", "경기도 수원시", RegionLevel.SIGUNGU, "41", true),
        Region("41111", "수원시 장안구", "경기도 수원시 장안구", RegionLevel.SIGUNGU, "41110", true),
    )
    private val regionRepository = mockk<RegionRepository> { every { findAll() } returns regions }
    private val placeRepository = mockk<PlaceRepository>()
    private val sut = TravelDestinationResolver(RegionCatalog(regionRepository), placeRepository)

    private val janganCafe = place(id = 7L, regionCode = "41111")

    init {
        every { placeRepository.findById(7L) } returns Optional.of(janganCafe)
        every { placeRepository.findById(99L) } returns Optional.empty()

        given("지역·장소가 모두 없으면") {
            then("미지정 지역, 장소 없음") {
                sut.resolve(null, null) shouldBe TravelDestination.UNSPECIFIED
            }
        }
        given("장소만 주면") {
            then("장소의 시·군·구를 지역으로 쓴다") {
                sut.resolve(null, 7L) shouldBe TravelDestination("41111", janganCafe)
            }
        }
        given("지역만 주면") {
            then("시·도도, 시·군·구도 그대로 쓴다") {
                sut.resolve("41", null) shouldBe TravelDestination("41", null)
                sut.resolve("11680", null) shouldBe TravelDestination("11680", null)
            }
            then("없는 코드나 미지정(00)은 400") {
                shouldThrow<InvalidInputException> { sut.resolve("99999", null) }
                shouldThrow<InvalidInputException> { sut.resolve("00", null) }
            }
        }
        given("지역과 장소를 함께 주면") {
            then("장소가 그 지역이거나 그 아래(상위 시·시·도)면 요청 지역을 쓴다") {
                sut.resolve("41111", 7L) shouldBe TravelDestination("41111", janganCafe)
                sut.resolve("41110", 7L) shouldBe TravelDestination("41110", janganCafe)
                sut.resolve("41", 7L) shouldBe TravelDestination("41", janganCafe)
            }
            then("장소가 다른 지역이면 400") {
                shouldThrow<InvalidInputException> { sut.resolve("11680", 7L) }
                shouldThrow<InvalidInputException> { sut.resolve("11", 7L) }
            }
        }
        given("없는 장소를 주면") {
            then("400") {
                shouldThrow<InvalidInputException> { sut.resolve(null, 99L) }
            }
        }
        given("수정 요청이 저장된 값을 그대로 다시 보내면") {
            // The place moved to another district in a later import: 11680 no longer contains it.
            val stored = TravelDestination("11680", janganCafe)
            then("계층 검사 없이 저장된 값을 유지한다") {
                sut.resolve("11680", 7L, current = stored) shouldBe stored
            }
            then("하나라도 바꾸면 다시 검사한다") {
                shouldThrow<InvalidInputException> { sut.resolve("11", 7L, current = stored) }
            }
        }

        // AT-002: 관광공사 contentid, shape only
        given("관광공사 장소 id를 주면") {
            then("지역과 함께면 그 지역, 장소(store)는 없음") {
                sut.resolve("11680", null, "126508") shouldBe TravelDestination("11680", null, "126508")
            }
            then("지역 없이 주면 미지정 지역") {
                sut.resolve(null, null, "126508") shouldBe TravelDestination("00", null, "126508")
            }
            then("숫자 1자리와 20자리는 경계값으로 허용") {
                sut.resolve(null, null, "1").tourContentId shouldBe "1"
                val twenty = "1".repeat(TravelDestination.TOUR_CONTENT_ID_MAX_LENGTH)
                sut.resolve(null, null, twenty).tourContentId shouldBe twenty
            }
            then("21자리, 빈 문자열, 공백, 문자 포함, 음수는 400") {
                val tooLong = "1".repeat(TravelDestination.TOUR_CONTENT_ID_MAX_LENGTH + 1)
                listOf(tooLong, "", " ", "12a", "-1", " 126508").forEach { invalid ->
                    shouldThrow<InvalidInputException> { sut.resolve(null, null, invalid) }
                }
            }
            then("지역 검사는 그대로: 없는 지역이면 400") {
                shouldThrow<InvalidInputException> { sut.resolve("99999", null, "126508") }
            }
        }
        given("store 장소와 관광공사 장소를 함께 주면") {
            then("장소 조회 전에 400") {
                shouldThrow<InvalidInputException> { sut.resolve(null, 7L, "126508") }
                // 99 does not exist: the single-place check must fire before the lookup.
                shouldThrow<InvalidInputException> { sut.resolve(null, 99L, "126508") }.message shouldBe
                    "Choose either a store place or a tour place, not both"
            }
        }
        given("관광공사 장소가 저장된 글을 수정할 때") {
            val stored = TravelDestination("11680", null, "126508")
            then("세 값이 같으면 저장된 값 그대로") {
                sut.resolve("11680", null, "126508", stored) shouldBe stored
            }
            then("지역만 바꾸면 새 지역 + 같은 id") {
                sut.resolve("11", null, "126508", stored) shouldBe TravelDestination("11", null, "126508")
            }
            then("id만 바꾸면 새 id") {
                sut.resolve("11680", null, "999", stored) shouldBe TravelDestination("11680", null, "999")
            }
            then("id를 빼면 지워지고, store 장소로 바꿀 수 있다") {
                sut.resolve("11680", null, null, stored) shouldBe TravelDestination("11680", null)
                sut.resolve(null, 7L, null, stored) shouldBe TravelDestination("41111", janganCafe)
            }
        }
    }

    private fun place(id: Long, regionCode: String): Place {
        val place = Place(
            source = PlaceSource.SBIZ, sourceKey = "K$id", name = "카페", branchName = null, searchName = "카페",
            categoryCode = "I21201", categoryName = "카페", regionCode = regionCode, roadAddress = "경기도 수원시 장안구 1",
            lat = 37.0, lng = 127.0, active = true,
        )
        Place::class.java.getDeclaredField("id").apply { isAccessible = true }.set(place, id)
        return place
    }
}
