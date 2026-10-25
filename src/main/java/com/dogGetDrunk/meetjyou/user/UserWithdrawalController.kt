package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

// Split from UserController (same path and Swagger tag) to keep its constructor under the detekt limit.
@RestControllerV1
@RequestMapping("/users")
@Tag(name = "유저 정보 API")
class UserWithdrawalController(
    private val accountExitService: AccountExitService,
) {
    @Operation(
        summary = "회원 탈퇴",
        description = "현재는 DELETE, 추후 PATCH로 변경될 수 있음. ${AccountExitService.APPLE_CODE_HEADER_DOC}",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
            ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 액세스 토큰입니다.",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
                ],
            ),
        ]
    )
    @DeleteMapping("/me")
    fun withdraw(
        @RequestHeader(AccountExitService.APPLE_AUTHORIZATION_CODE_HEADER, required = false)
        appleAuthorizationCode: String?,
    ) {
        accountExitService.withdraw(appleAuthorizationCode)
    }
}
