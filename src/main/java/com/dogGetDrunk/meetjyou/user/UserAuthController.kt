package com.dogGetDrunk.meetjyou.user

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse
import com.dogGetDrunk.meetjyou.user.dto.LoginRequest
import com.dogGetDrunk.meetjyou.user.dto.RefreshTokenRequest
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequest
import com.dogGetDrunk.meetjyou.user.dto.TokenResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "User authentication controller", description = "유저 인증 관련 API")
class UserAuthController(
    private val userAuthService: UserAuthService
) {
    @Operation(summary = "유저 회원가입", description = "이메일로 회원 가입한다.")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "201",
            description = "회원가입이 완료되었습니다.",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenResponse::class)
                )
            )
        ), ApiResponse(
            responseCode = "409",
            description = "이미 가입한 이메일입니다.",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )
            )
        )]
    )
    @PostMapping("/registration")
    fun register(@Valid @RequestBody request: RegistrationRequest): ResponseEntity<TokenResponse> {
        val response = userAuthService.registerViaSocial(request)
        return ResponseEntity.created(URI.create("/" + response.uuid))
            .body(response)
    }

    @Operation(summary = "유저 로그인", description = "이메일로 로그인한다.")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenResponse::class)
                )
            )
        ), ApiResponse(
            responseCode = "404",
            description = "가입되지 않은 유저입니다.",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )
            )
        )]
    )
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<TokenResponse> {
        val tokenResponseDto = userAuthService.loginViaSocial(request)
        return ResponseEntity.ok(tokenResponseDto)
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰을 이용해 새로운 액세스 토큰 및 리프레시 토큰을 발급한다.")
    @ApiResponses(
        value = [ApiResponse(
            responseCode = "200",
            description = "토큰 갱신 성공",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenResponse::class)
                )
            )
        ), ApiResponse(
            responseCode = "401",
            description = "유효하지 않은 토큰",
            content = arrayOf(
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ErrorResponse::class)
                )
            )
        )]
    )
    @PostMapping("/refresh")
    fun refreshToken(
        @RequestHeader("Authorization") authorizationHeader: String,
        @RequestBody requestDto: RefreshTokenRequest,
    ): ResponseEntity<TokenResponse> {
        val refreshToken = authorizationHeader.substring("Bearer ".length)
        val tokenResponseDto = userAuthService.refreshToken(refreshToken, requestDto)

        return ResponseEntity.ok(tokenResponseDto)
    }
}
