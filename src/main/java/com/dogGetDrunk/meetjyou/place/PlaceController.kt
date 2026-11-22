package com.dogGetDrunk.meetjyou.place

import com.dogGetDrunk.meetjyou.config.RestControllerV1
import com.dogGetDrunk.meetjyou.place.dto.PlaceSearchResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RestControllerV1
@RequestMapping("/places")
@Tag(name = "여행지 검색 API")
class PlaceController(
    private val placeSearchFacade: PlaceSearchFacade,
) {
    @Operation(
        summary = "여행지 검색",
        description = TOUR_SEARCH_DESCRIPTION,
    )
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) region: String?,
        @RequestParam(defaultValue = "${PlaceSearchService.DEFAULT_SIZE}") size: Int,
    ): ResponseEntity<PlaceSearchResponse> =
        ResponseEntity.ok(placeSearchFacade.search(q, region, size))

    private companion object {
        const val TOUR_SEARCH_DESCRIPTION = """검색어 하나로 지역(시·도, 시·군·구), 가게(places), 관광지(tourPlaces, 한국관광공사 실시간)를 함께 찾습니다.
region을 주면 그 지역과 하위 지역의 결과만 찾습니다.
관광공사 조회가 실패하면 tourPlaces는 비고 tourStatus=FAILED, 서버에 키가 없으면 DISABLED입니다.
관광지는 "출처 : ⓒ한국관광콘텐츠랩"을 표기해야 합니다."""
    }
}
