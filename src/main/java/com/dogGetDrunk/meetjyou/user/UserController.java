package com.dogGetDrunk.meetjyou.user;

import com.dogGetDrunk.meetjyou.post.PostResponseDto;
import com.dogGetDrunk.meetjyou.post.PostService;
import com.dogGetDrunk.meetjyou.user.dto.AdvancedUserResponseDto;
import com.dogGetDrunk.meetjyou.user.dto.BasicUserResponse;
import com.dogGetDrunk.meetjyou.user.dto.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "유저 정보 API")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    @Operation(summary = "유저의 기본 정보 조회", description = "유저의 기본 정보(닉네임, 한 줄 소개, 프사 썸네일, 성별, 나이) 및 라이프스타일(성격, 여행 스타일, 식단, 기타)을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BasicUserResponse.class))),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/basic-info")
    public ResponseEntity<BasicUserResponse> getBasicUserProfile(@PathVariable Long id) {
        BasicUserResponse response = userService.getUserProfile(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "유저의 모든 정보 조회", description = "유저의 기본 정보, 라이프스타일 및 작성한 모집글을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdvancedUserResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/advanced-info")
    public ResponseEntity<AdvancedUserResponseDto> getAdvancedUserProfile(@PathVariable Long id) {
        BasicUserResponse basicUserResponseDto = userService.getUserProfile(id);
        List<PostResponseDto> posts = postService.getPostsByAuthorId(id);
        return ResponseEntity.ok(new AdvancedUserResponseDto(basicUserResponseDto, posts));
    }

    @Operation(summary = "유저 정보 수정", description = "유저의 닉네임, 한 줄 소개, 성별, 나이 및 라이프스타일을 수정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "수정 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BasicUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "유저를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    public ResponseEntity<BasicUserResponse> updateUser(@PathVariable Long id, @RequestBody @Valid UserUpdateRequest requestDto) {
        BasicUserResponse updatedUser = userService.updateUser(id, requestDto);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "[admin] 모든 유저 프로필 조회")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(
                    mediaType = "application/json", schema = @Schema(type = "array", implementation = BasicUserResponse.class))),
    })
    @GetMapping
    public ResponseEntity<List<BasicUserResponse>> getAllUsersProfile() {
        return ResponseEntity.ok(userService.getAllUsersProfile());
    }
}
