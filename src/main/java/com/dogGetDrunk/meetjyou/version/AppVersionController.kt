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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RestControllerV1
@RequestMapping("/{platform}/versions")
@Tag(name = "앱 버전 관리", description = "iOS/Android 앱 버전 관련 API")
class AppVersionController(
    private val appVersionService: AppVersionService,
) {
    @Operation(
        summary = "버전 체크",
        description = "클라이언트 버전을 서버에 전달해 강제/선택 업데이트 여부를 확인합니다. " +
            "스토어 배포가 확인된(storeReleased=true) 버전 중 forceUpdate=true인 가장 높은 버전이 최소 요구 버전입니다.",
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
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping
    fun getAllVersions(@PathVariable platform: String): ResponseEntity<List<AppVersionDto>> =
        ResponseEntity.ok(appVersionService.getAllVersions(toPlatform(platform)))


    @Operation(
        summary = "[admin] 새 버전 등록",
        description = "등록 직후엔 항상 storeReleased=false(미배포)로 생성되어 /check 판단에 영향을 주지 않습니다. " +
            "스토어 심사 통과 및 실제 배포를 확인한 뒤 [admin] 스토어 배포 확인 토글 API로 반영하세요.",
    )
    @ApiResponses(
        value = [ApiResponse(responseCode = "201", description = "새 버전 등록 성공"),
            ApiResponse(
                responseCode = "409",
                description = "이미 등록된 버전",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            )]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping
    fun addVersion(
        @PathVariable platform: String,
        @RequestBody dto: AppVersionDto,
    ): ResponseEntity<Void> {
        appVersionService.addVersion(toPlatform(platform), dto)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }


    @Operation(summary = "[admin] 버전 정보 수정", description = "강제 업데이트 여부 및 업데이트 안내 메시지를 수정합니다.")
    @ApiResponses(
        value = [ApiResponse(responseCode = "200", description = "버전 정보 수정 완료"),
            ApiResponse(
                responseCode = "404",
                description = "등록되지 않은 버전",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
            )]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
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
        summary = "[admin] 강제 업데이트 마커 설정",
        description = "이 버전을 강제 업데이트 최소 기준 마커로 사용할지 명시적으로 설정합니다. " +
            "동일한 값으로 반복 호출해도 결과가 바뀌지 않는 멱등 연산입니다.",
    )
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "변경 완료",
            content = [Content(mediaType = "application/json", schema = Schema(example = "{ \"message\": \"Force update set: true -> false\" }"))],
        ), ApiResponse(
            responseCode = "404",
            description = "등록되지 않은 버전",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
        )]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/{version}/force-update")
    fun setForceUpdate(
        @PathVariable platform: String,
        @PathVariable version: String,
        @RequestParam forceUpdate: Boolean,
    ): ResponseEntity<Map<String, String>> {
        val previous = appVersionService.setForceUpdate(version, toPlatform(platform), forceUpdate)
        return ResponseEntity.ok(mapOf("message" to "Force update set: $previous -> $forceUpdate"))
    }


    @Operation(
        summary = "[admin] 스토어 배포 확인 설정",
        description = "스토어 심사 통과 및 실제 배포가 확인된 버전만 최신/강제 업데이트 판단에 반영됩니다. " +
            "등록 직후엔 기본적으로 false이며, 실제 다운로드 가능 여부를 확인한 뒤에만 true로 설정하세요. " +
            "동일한 값으로 반복 호출해도 결과가 바뀌지 않는 멱등 연산입니다.",
    )
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "변경 완료",
            content = [Content(mediaType = "application/json", schema = Schema(example = "{ \"message\": \"Store release set: false -> true\" }"))],
        ), ApiResponse(
            responseCode = "404",
            description = "등록되지 않은 버전",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
        )]
    )
    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/{version}/release")
    fun setStoreReleased(
        @PathVariable platform: String,
        @PathVariable version: String,
        @RequestParam storeReleased: Boolean,
    ): ResponseEntity<Map<String, String>> {
        val previous = appVersionService.setStoreReleased(version, toPlatform(platform), storeReleased)
        return ResponseEntity.ok(mapOf("message" to "Store release set: $previous -> $storeReleased"))
    }


    @Operation(
        summary = "[admin] 스토어 다운로드 URL 변경",
        description = "플랫폼의 App Store/Play Store 다운로드 URL을 변경합니다. 특정 버전이 아닌 플랫폼 단위로 관리됩니다.",
    )
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "다운로드 URL 변경 성공")])
    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/store-url")
    fun updateStoreUrl(
        @PathVariable platform: String,
        @RequestParam newUrl: String,
    ): ResponseEntity<Void> {
        appVersionService.updateStoreUrl(toPlatform(platform), newUrl)
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
    @PreAuthorize("hasAuthority('ADMIN')")
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
