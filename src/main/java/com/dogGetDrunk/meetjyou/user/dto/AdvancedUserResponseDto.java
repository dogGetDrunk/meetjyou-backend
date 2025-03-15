package com.dogGetDrunk.meetjyou.user.dto;

import com.dogGetDrunk.meetjyou.post.PostResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdvancedUserResponseDto {
    private BasicUserResponse basicUserInfo;
    private List<PostResponseDto> posts;
}
