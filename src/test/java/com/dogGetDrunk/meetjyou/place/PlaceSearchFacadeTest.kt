package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchItem
import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchResponse
import com.dogGetDrunk.meetjyou.place.dto.RegionResponse
import com.dogGetDrunk.meetjyou.place.tour.TourPlaceSearch
import com.dogGetDrunk.meetjyou.place.tour.TourSearchResult
import com.dogGetDrunk.meetjyou.place.tour.TourSearchStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/** AT-003: a failed tour group keeps the regions and stores of the same search. */
class PlaceSearchFacadeTest : BehaviorSpec() {
    init {
        given("관광공사 결과가 FAILED일 때") {
            then("지역과 가게 결과는 그대로 두고 관광지 묶음만 비운다") {
                val region = RegionResponse("11", "서울특별시", "서울특별시", RegionLevel.SIDO, "11", "서울특별시", null, null)
                val store = PlaceSearchItem(1, "카페", null, "카페", "도로명 1", 37.0, 127.0, region)
                val local = PlaceSearchResponse(listOf(region), listOf(store))
                val service = mockk<PlaceSearchService> { every { search("카페", null, 20) } returns local }
                val tour = mockk<TourPlaceSearch> { every { search("카페", null, 20) } returns TourSearchResult.FAILED }

                val response = PlaceSearchFacade(service, tour).search("카페", null, 20)

                response.regions shouldBe listOf(region)
                response.places shouldBe listOf(store)
                response.tourPlaces.shouldBeEmpty()
                response.tourStatus shouldBe TourSearchStatus.FAILED
            }
        }
    }
}
