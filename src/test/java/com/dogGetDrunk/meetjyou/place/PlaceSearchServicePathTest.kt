package com.dogGetDrunk.meetjyou.place

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

// Which path fills the page after exact names. Results of both paths are pinned by PlaceSearchPathDockerTest.
class PlaceSearchServicePathTest : BehaviorSpec() {
    init {
        given("지역 필터가 있을 때") {
            then("전국 일치가 기준값 이하면 FTS 경로") {
                val repository = repository(matches = PlaceSearchService.FULL_TEXT_MAX_MATCHES)
                service(repository).search("카페", SIDO, SIZE)
                verify(exactly = 1) { repository.searchFullText(any(), setOf(SIDO, DISTRICT), SIZE) }
                verify(exactly = 0) { repository.searchRegionIndex(any(), any(), any()) }
            }
            then("기준값을 넘으면 지역 인덱스 경로") {
                val repository = repository(matches = PlaceSearchService.FULL_TEXT_MAX_MATCHES + 1)
                service(repository).search("카페", SIDO, SIZE)
                verify(exactly = 1) { repository.searchRegionIndex(any(), setOf(SIDO, DISTRICT), SIZE) }
                verify(exactly = 0) { repository.searchFullText(any(), any(), any()) }
            }
        }
        given("지역 필터가 없을 때") {
            then("일치 건수를 세지 않고 FTS 경로") {
                val repository = repository(matches = Long.MAX_VALUE)
                service(repository).search("카페", null, SIZE)
                verify(exactly = 1) { repository.searchFullText(any(), null, SIZE) }
                verify(exactly = 0) { repository.countFullTextMatches(any()) }
            }
        }
        given("쓸 토큰이 없는 검색어") {
            then("어느 경로도 조회하지 않는다") {
                val repository = repository(matches = Long.MAX_VALUE)
                service(repository).search("asia", SIDO, SIZE)
                verify(exactly = 0) { repository.countFullTextMatches(any()) }
                verify(exactly = 0) { repository.findExact(any(), any(), any()) }
            }
        }
    }

    // Built per leaf: a spec instance may run several leaves, and mock call records would carry over.
    private fun repository(matches: Long): PlaceSearchRepository = mockk {
        every { findExact(any(), any(), any()) } returns emptyList()
        every { countFullTextMatches(any()) } returns matches
        every { searchFullText(any(), any(), any()) } returns emptyList()
        every { searchRegionIndex(any(), any(), any()) } returns emptyList()
    }

    private fun service(repository: PlaceSearchRepository): PlaceSearchService {
        val regions = listOf(
            Region(Region.UNSPECIFIED_CODE, "미지정", "미지정", RegionLevel.SIDO, null, false),
            Region(SIDO, "서울특별시", "서울특별시", RegionLevel.SIDO, null, true),
            Region(DISTRICT, "강남구", "서울특별시 강남구", RegionLevel.SIGUNGU, SIDO, true),
        )
        val catalog = RegionCatalog(mockk<RegionRepository> { every { findAll() } returns regions })
        return PlaceSearchService(catalog, repository)
    }

    private companion object {
        const val SIDO = "11"
        const val DISTRICT = "11680"
        const val SIZE = 20
    }
}
