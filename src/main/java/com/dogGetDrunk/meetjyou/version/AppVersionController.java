package com.dogGetDrunk.meetjyou.version;

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ios/versions")
@Tag(name = "iOS 앱 버전", description = "iOS 앱 버전 관련 API")
public class AppVersionController {

    private final AppVersionService appVersionService;


    @Operation(summary = "최신 버전 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "최신 버전 조회 성공", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = AppVersionDto.class))),
    })
    @GetMapping("/latest")
    public ResponseEntity<AppVersionDto> getLatestVersion() {
        return ResponseEntity.ok(appVersionService.getLatestVersion());
    }


    @Operation(summary = "강제 업데이트 여부 확인")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "강제 업데이트 여부 확인 성공", content = @Content(
                    mediaType = "application/json", schema = @Schema(example = "{ \"forceUpdate\": true }"))),
            @ApiResponse(responseCode = "404", description = "등록되지 않은 버전입니다.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @GetMapping("/check-force-update")
    public ResponseEntity<Map<String, Boolean>> checkForceUpdate(@RequestParam String version) {
        boolean forceUpdate = appVersionService.isForceUpdate(version);
        return ResponseEntity.ok(Map.of("forceUpdate", forceUpdate));
    }


    @Operation(summary = "[admin] 모든 버전 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "모든 버전 조회 성공", content = @Content(
                    mediaType = "application/json", schema = @Schema(type = "array", implementation = AppVersionDto.class))),
    })
    @GetMapping
    public ResponseEntity<List<AppVersionDto>> getAllVersions() {
        return ResponseEntity.ok(appVersionService.getAllVersions());
    }


    @Operation(summary = "[admin] 새 버전 등록")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "새 버전 등록 성공"),
            @ApiResponse(responseCode = "409", description = "이미 등록된 버전입니다.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @PostMapping
    public void addVersion(@RequestBody AppVersionDto appVersionDto) {
        appVersionService.addVersion(appVersionDto);
    }


    @Operation(summary = "[admin] 버전 정보 수정", description = "강제 업데이트 여부 및 다운로드 url을 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "버전 정보 수정 완료"),
            @ApiResponse(responseCode = "404", description = "등록되지 않은 버전입니다.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @PutMapping("/{version}")
    public void updateVersion(
            @PathVariable String version,
            @RequestBody AppVersionDto appVersionDto) {
        appVersionService.updateVersion(version, appVersionDto);
    }


    @Operation(summary = "[admin] 강제 업데이트 설정/해제", description = "강제 업데이트가 설정되어 있다면 해제하고, 해제되어 있다면 설정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "강제 업데이트 설정/해제 완료", content = @Content(
                    mediaType = "application/json", schema = @Schema(example = "{ \"message\": \"강제 업데이트 설정이 변경되었습니다: true -> false\" }"))),
            @ApiResponse(responseCode = "404", description = "등록되지 않은 버전입니다.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @PutMapping("/{version}/force-update")
    public ResponseEntity<Map<String, String>> toggleForceUpdate(@PathVariable String version) {
        boolean curForceUpdate = appVersionService.toggleForceUpdate(version);
        return ResponseEntity.ok(Map.of(
                "message",
                MessageFormat.format("강제 업데이트 설정이 변경되었습니다: {0} -> {1}", !curForceUpdate, curForceUpdate)
        ));
    }


    @Operation(summary = "[admin] 다운로드 url 변경")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "다운로드 url 변경 성공"),
            @ApiResponse(responseCode = "404", description = "등록되지 않은 버전입니다.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @PutMapping("/{version}/download-url")
    public void updateDownloadUrl(@PathVariable String version, String newUrl) {
        appVersionService.updateDownloadUrl(version, newUrl);
    }

    @Operation(summary = "[admin] 버전 정보 삭제")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "버전 정보 삭제 완료", content = @Content(
                    mediaType = "application/json", schema = @Schema(example = "{ \"message\": \"버전 정보가 삭제되었습니다.\" }"))),
            @ApiResponse(responseCode = "404", description = "등록되지 않은 버전입니다.", content = @Content(
                    mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @DeleteMapping("/{version}")
    public void deleteVersion(@PathVariable String version) {
        appVersionService.deleteVersion(version);
    }
}
