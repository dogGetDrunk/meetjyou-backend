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
                sut.resolve("11680", 7L, stored) shouldBe stored
            }
            then("하나라도 바꾸면 다시 검사한다") {
                shouldThrow<InvalidInputException> { sut.resolve("11", 7L, stored) }
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
