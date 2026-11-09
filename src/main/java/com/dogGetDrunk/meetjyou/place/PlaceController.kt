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
    private val placeSearchService: PlaceSearchService,
) {
    @Operation(
        summary = "여행지 검색",
        description = "검색어 하나로 지역(시·도, 시·군·구)과 장소를 함께 찾습니다. region을 주면 그 지역과 하위 지역의 장소만 찾습니다.",
    )
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) region: String?,
        @RequestParam(defaultValue = "${PlaceSearchService.DEFAULT_SIZE}") size: Int,
    ): ResponseEntity<PlaceSearchResponse> =
        ResponseEntity.ok(placeSearchService.search(q, region, size))
}
