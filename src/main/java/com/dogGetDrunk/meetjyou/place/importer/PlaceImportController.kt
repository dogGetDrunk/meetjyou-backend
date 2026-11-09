package com.dogGetDrunk.meetjyou.place.importer

import com.dogGetDrunk.meetjyou.config.RestControllerV1
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@RestControllerV1
@RequestMapping("/admin/places/imports")
@Tag(name = "여행지 데이터 적재 API (관리자)")
class PlaceImportController(
    private val placeImportService: PlaceImportService,
) {
    @Operation(
        summary = "소상공인 상가 데이터 적재 시작",
        description = "공공데이터포털에서 최신 파일을 받아 백그라운드로 적재합니다. 실행 중이면 409.",
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    fun start(): ResponseEntity<PlaceImportStatusResponse> =
        ResponseEntity.accepted().body(placeImportService.start())

    @Operation(summary = "최근 적재 상태 조회", description = "이 서버 프로세스의 마지막 적재 결과입니다. 재시작하면 IDLE.")
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/latest")
    fun latest(): ResponseEntity<PlaceImportStatusResponse> =
        ResponseEntity.ok(placeImportService.latest())
}
