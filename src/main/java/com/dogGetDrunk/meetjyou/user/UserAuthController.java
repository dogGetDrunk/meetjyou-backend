package com.dogGetDrunk.meetjyou.user;

import com.dogGetDrunk.meetjyou.common.exception.ErrorResponse;
import com.dogGetDrunk.meetjyou.common.exception.business.InvalidAuthorizationHeaderException;
import com.dogGetDrunk.meetjyou.user.dto.LoginRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.RefreshTokenRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.RegistrationRequestDto;
import com.dogGetDrunk.meetjyou.user.dto.TokenResponseDto;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "User authentication controller", description = "유저 인증 관련 API")
public class UserAuthController {

    private final UserService userService;

    @Operation(summary = "유저 회원가입", description = "이메일로 회원 가입한다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "회원가입이 완료되었습니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "이미 가입한 이메일입니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @PostMapping("/registration")
    public ResponseEntity<TokenResponseDto> register(@RequestBody RegistrationRequestDto registrationRequestDto) {
        TokenResponseDto tokenResponseDto = userService.createUser(registrationRequestDto);
        return ResponseEntity.created(URI.create("/" + registrationRequestDto.getEmail()))
                .body(tokenResponseDto);
    }


    @Operation(summary = "유저 닉네임 중복 확인")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "complete",
                    content = @Content(schema = @Schema(example = "{ \"isDuplicate\": true }")))
    })
    @GetMapping("/is-duplicate-nickname")
    public ResponseEntity<Map<String, Boolean>> checkNicknameDuplication(@RequestParam String nickname) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("isDuplicate", userService.isDuplicateNickname(nickname));

        return ResponseEntity.ok(response);
    }


    @Operation(summary = "유저 로그인", description = "이메일로 로그인한다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "가입되지 않은 이메일입니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@RequestBody LoginRequestDto loginRequestDto) {
        TokenResponseDto tokenResponseDto = userService.login(loginRequestDto);
        return ResponseEntity.ok(tokenResponseDto);
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰을 이용해 새로운 액세스 토큰 및 리프레시 토큰을 발급한다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDto> refreshToken(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody RefreshTokenRequestDto requestDto
    ) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidAuthorizationHeaderException(authorizationHeader);
        }

        String refreshToken = authorizationHeader.substring("Bearer ".length());
        TokenResponseDto tokenResponseDto = userService.refreshToken(refreshToken, requestDto.getEmail());

        return ResponseEntity.ok(tokenResponseDto);
    }

    @Operation(summary = "회원 탈퇴", description = "현재는 DELETE, 추후 PATCH로 변경될 수 있음.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 액세스 토큰입니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
    })
    @DeleteMapping("/")
    public void withdraw(@RequestHeader("Authorization") String authorizationHeader, @RequestParam String email) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidAuthorizationHeaderException(authorizationHeader);
        }

        String accessToken = authorizationHeader.substring("Bearer ".length());
        userService.withdrawUser(email, accessToken);
    }
}
