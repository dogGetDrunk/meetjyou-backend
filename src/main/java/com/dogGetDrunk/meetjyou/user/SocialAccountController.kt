package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.config.RestControllerV1
import com.dogGetDrunk.meetjyou.user.UserAuthController.Companion.SESSION_SOCIAL_NONCE
import com.dogGetDrunk.meetjyou.user.dto.LinkSocialAccountRequest
import com.dogGetDrunk.meetjyou.user.dto.SocialAccountResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

// Not under /auth/: JwtAuthFilter skips that prefix, and these endpoints need the signed-in user.
@RestControllerV1
@RequestMapping("/users/me/social-accounts")
@Tag(name = "소셜 계정 연결 API")
class SocialAccountController(
    private val socialAccountService: SocialAccountService,
) {
    @Operation(summary = "연결된 소셜 계정 목록", description = "내 계정에 연결된 소셜 계정을 연결한 순서대로 조회합니다.")
    @GetMapping
    fun getMySocialAccounts(): ResponseEntity<List<SocialAccountResponse>> =
        ResponseEntity.ok(socialAccountService.getMySocialAccounts())

    @Operation(
        summary = "소셜 계정 연결",
        description = "소셜 로그인과 같은 방식(`/auth/nonce`로 받은 세션의 nonce + 토큰)으로 다른 소셜 계정을 내 계정에 연결합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "새로 연결됨"),
            ApiResponse(responseCode = "200", description = "이미 내 계정에 연결된 소셜 계정 (재시도)"),
            ApiResponse(
                responseCode = "409",
                description = "다른 유저에게 연결된 소셜 계정이거나, 같은 provider의 다른 계정이 이미 연결됨",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
                ],
            ),
        ]
    )
    @PostMapping
    fun link(
        @Valid @RequestBody request: LinkSocialAccountRequest,
        session: HttpSession,
    ): ResponseEntity<SocialAccountResponse> {
        val nonce = session.getAttribute(SESSION_SOCIAL_NONCE) as String?
        val result = socialAccountService.link(request, nonce)
        session.removeAttribute(SESSION_SOCIAL_NONCE)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.account)
    }

    @Operation(summary = "소셜 계정 연결 해제", description = "마지막 남은 소셜 계정은 해제할 수 없습니다.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "해제됨"),
            ApiResponse(
                responseCode = "400",
                description = "마지막 남은 소셜 계정",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "연결되지 않은 provider",
                content = [
                    Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
                ],
            ),
        ]
    )
    @DeleteMapping("/{provider}")
    fun unlink(@PathVariable provider: AuthProvider): ResponseEntity<Void> {
        socialAccountService.unlink(provider)
        return ResponseEntity.noContent().build()
    }
}
