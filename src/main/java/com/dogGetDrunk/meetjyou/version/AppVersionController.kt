package com.dogGetDrunk.meetjyou.version

import com.dogGetDrunk.meetjyou.common.exception.ErrorCode
import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidInputException
import com.dogGetDrunk.meetjyou.version.dto.VersionCheckResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/{platform}/versions")
@Tag(name = "앱 버전 관리", description = "iOS/Android 앱 버전 관련 API")
class AppVersionController(
    private val appVersionService: AppVersionService,
) {
    @Operation(
        summary = "버전 체크",
        description = "클라이언트 버전을 서버에 전달해 강제/선택 업데이트 여부를 확인합니다. " +
            "forceUpdate=true인 버전 중 가장 높은 버전이 최소 요구 버전입니다.",
    )
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "버전 체크 성공",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = VersionCheckResponse::class))],
        ), ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 platform 값",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
        )]
    )
    @GetMapping("/check")
    fun checkVersion(
        @PathVariable platform: String,
        @RequestParam clientVersion: String,
    ): ResponseEntity<VersionCheckResponse> =
        ResponseEntity.ok(appVersionService.checkVersion(toPlatform(platform), clientVersion))


    @Operation(summary = "최신 버전 조회")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "최신 버전 조회 성공",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = AppVersionDto::class))],
        ), ApiResponse(
            responseCode = "404",
            description = "등록된 버전 없음",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
        )]
    )
    @GetMapping("/latest")
    fun getLatestVersion(@PathVariable platform: String): ResponseEntity<AppVersionDto> =
        ResponseEntity.ok(appVersionService.getLatestVersion(toPlatform(platform)))


    @Operation(summary = "[admin] 모든 버전 조회")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "모든 버전 조회 성공",
            content = [Content(mediaType = "application/json", schema = Schema(type = "array", implementation = AppVersionDto::class))],
        )]
    )
    @GetMapping
    fun getAllVersions(@PathVariable platform: String): ResponseEntity<List<AppVersionDto>> =
        ResponseEntity.ok(appVersionService.getAllVersions(toPlatform(platform)))


    @Operation(
        summary = "[admin] 새 버전 등록",
        description = "forceUpdate=true로 등록된 버전 중 가장 높은 버전이 강제 업데이트 최소 기준이 됩니다.",
    )
    @ApiResponses(
        value = [ApiResponse(responseCode = "201", description = "새 버전 등록 성공"),
            ApiResponse(
                responseCode = "409",
                description = "이미 등록된 버전",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            )]
    )
    @PostMapping
    fun addVersion(
        @PathVariable platform: String,
        @RequestBody dto: AppVersionDto,
    ): ResponseEntity<Void> {
        appVersionService.addVersion(toPlatform(platform), dto)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }


    @Operation(summary = "[admin] 버전 정보 수정", description = "강제 업데이트 여부 및 다운로드 URL을 수정합니다.")
    @ApiResponses(
        value = [ApiResponse(responseCode = "200", description = "버전 정보 수정 완료"),
            ApiResponse(
                responseCode = "404",
                description = "등록되지 않은 버전",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            )]
    )
    @PutMapping("/{version}")
    fun updateVersion(
        @PathVariable platform: String,
        @PathVariable version: String,
        @RequestBody dto: AppVersionDto,
    ): ResponseEntity<Void> {
        appVersionService.updateVersion(version, toPlatform(platform), dto)
        return ResponseEntity.ok().build()
    }


    @Operation(
        summary = "[admin] 강제 업데이트 마커 설정/해제",
        description = "이 버전을 강제 업데이트 최소 기준 마커로 사용할지 토글합니다.",
    )
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "변경 완료",
            content = [Content(mediaType = "application/json", schema = Schema(example = "{ \"message\": \"Force update toggled: true -> false\" }"))],
        ), ApiResponse(
            responseCode = "404",
            description = "등록되지 않은 버전",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
        )]
    )
    @PutMapping("/{version}/force-update")
    fun toggleForceUpdate(
        @PathVariable platform: String,
        @PathVariable version: String,
    ): ResponseEntity<Map<String, String>> {
        val current = appVersionService.toggleForceUpdate(version, toPlatform(platform))
        return ResponseEntity.ok(mapOf("message" to "Force update toggled: ${!current} -> $current"))
    }


    @Operation(summary = "[admin] 다운로드 URL 변경")
    @ApiResponses(
        value = [ApiResponse(responseCode = "200", description = "다운로드 URL 변경 성공"),
            ApiResponse(
                responseCode = "404",
                description = "등록되지 않은 버전",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            )]
    )
    @PutMapping("/{version}/download-url")
    fun updateDownloadUrl(
        @PathVariable platform: String,
        @PathVariable version: String,
        @RequestParam newUrl: String,
    ): ResponseEntity<Void> {
        appVersionService.updateDownloadUrl(version, toPlatform(platform), newUrl)
        return ResponseEntity.ok().build()
    }


    @Operation(summary = "[admin] 버전 삭제")
    @ApiResponses(
        value = [ApiResponse(responseCode = "200", description = "버전 삭제 완료"),
            ApiResponse(
                responseCode = "404",
                description = "등록되지 않은 버전",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            )]
    )
    @DeleteMapping("/{version}")
    fun deleteVersion(
        @PathVariable platform: String,
        @PathVariable version: String,
    ): ResponseEntity<Void> {
        appVersionService.deleteVersion(version, toPlatform(platform))
        return ResponseEntity.ok().build()
    }

    private fun toPlatform(value: String): Platform =
        runCatching { Platform.valueOf(value.uppercase()) }.getOrElse {
            throw InvalidInputException(
                errorCode = ErrorCode.INVALID_INPUT_VALUE,
                value = value,
                message = "Platform must be one of: ${Platform.entries.joinToString { it.name.lowercase() }}",
            )
        }
}
